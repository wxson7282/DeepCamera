package com.example.security_camera.camera_manager

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.example.security_camera.streaming.OnEncodedDataListener
import java.io.File

/**
 * 视频编码器模块
 *
 * 职责：
 * 1. MediaCodec 初始化和配置（H.264/H.265 编码）
 * 2. MediaMuxer 封装为 MP4
 * 3. YUV 帧数据编码
 * 4. 编码器生命周期管理
 * 5. 编码数据双路输出：MediaMuxer（本地录像）+ OnEncodedDataListener（网络流）
 *
 * @param width 视频宽度
 * @param height 视频高度
 * @param frameRate 帧率
 * @param bitRate 码率
 * @param outputFile 输出文件
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitRate: Int,
    private val outputFile: File
) {
    companion object {
        private const val TAG = "VideoEncoder"
        const val I_FRAME_INTERVAL = 1 // I 帧间隔（秒）
        const val TIMEOUT_US = 10000L
    }

    // MediaCodec 编码器
    private var mediaCodec: MediaCodec? = null

    // MediaMuxer 封装器
    private var mediaMuxer: MediaMuxer? = null

    // 轨道索引
    private var trackIndex = -1

    // Muxer 是否已启动
    private var muxerStarted = false

    // 编码器锁
    private val encoderLock = Any()

    // ★ 编码数据监听器（用于网络流传输）
    @Volatile
    private var encodedDataListener: OnEncodedDataListener? = null

    // 编码器MIME类型
    private val mimeType = if (HevcSupportUtils.isHevcEncoderSupportedForSize(width, height, bitRate, frameRate)) {
        Log.i(TAG, "使用 HEVC H265 编码器")
        MediaFormat.MIMETYPE_VIDEO_HEVC
    } else {
        Log.i(TAG, "使用 AVC H264 编码器")
        MediaFormat.MIMETYPE_VIDEO_AVC
    }

    init {
        initEncoder()
        initMuxer()
    }

    // ==================== 监听器 ====================

    /**
     * 设置编码数据监听器
     *
     * 设置后，编码输出的数据会同时发送给监听器，用于网络流传输。
     * 传 null 则移除监听器，不影响本地录像。
     */
    fun setEncodedDataListener(listener: OnEncodedDataListener?) {
        encodedDataListener = listener
        Log.i(TAG, "编码数据监听器: ${if (listener != null) "已设置" else "已移除"}")
    }

    // ==================== 初始化 ====================

    /**
     * 初始化 MediaCodec 编码器
     */
    private fun initEncoder() {
        try {
            // 创建视频编码格式
            val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            }

            // 创建配置并启动编码器
            mediaCodec = MediaCodec.createEncoderByType(mimeType).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            Log.i(TAG, "编码器初始化成功: ${width}x${height} @ ${frameRate}fps, bitrate=$bitRate")

        } catch (e: Exception) {
            Log.e(TAG, "编码器初始化失败", e)
        }
    }

    /**
     * 初始化 MediaMuxer 封装器
     */
    private fun initMuxer() {
        try {
            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mediaMuxer?.setOrientationHint(90) // 顺时针旋转 90 度
            Log.i(TAG, "Muxer 初始化成功: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Muxer 初始化失败", e)
        }
    }

    /**
     * 编码单帧 YUV 数据
     *
     * @param watermarkedYuv YUV 原始数据（YUV420 格式）
     * @param presentationTimeNs 时间戳（纳秒）
     */
    @SuppressLint("WrongConstant")
    fun encodeFrame(watermarkedYuv: ByteArray, presentationTimeNs: Long) {
        synchronized(encoderLock) {
            val codec = mediaCodec ?: return

            try {
                // 数据出列存入缓冲区
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
                    inputBuffer.clear()

                    // 将 YUV 数据写入输入缓冲区
                    val ySize = width * height
                    val uvSize = ySize / 2
                    // Y plane
                    inputBuffer.put(watermarkedYuv, 0, ySize)
                    // UV plane
                    inputBuffer.put(watermarkedYuv, ySize, uvSize)

                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        watermarkedYuv.size,
                        presentationTimeNs / 1000, // 转换为微秒
                        0
                    )
                }

                // 处理输出缓冲区
                processOutputBuffer()

            } catch (e: Exception) {
                Log.e(TAG, "编码帧失败", e)
            }
        }
    }

    /**
     * 处理编码器输出缓冲区
     *
     * ★ 改造点：输出数据同时发给 MediaMuxer 和 OnEncodedDataListener
     */
    private fun processOutputBuffer() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(TAG, "编码器输出格式改变: $newFormat")

                        synchronized(encoderLock) {
                            if (!muxerStarted) {
                                trackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                                mediaMuxer?.start()
                                muxerStarted = true
                            }
                        }

                        // ★ 通知监听器格式变化（携带 SPS/PPS）
                        encodedDataListener?.onFormatChanged(newFormat)
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue

                        // ★ 先复制数据给监听器（在 releaseOutputBuffer 之前）
                        encodedDataListener?.let { listener ->
                            if (bufferInfo.size > 0) {
                                val data = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.get(data)
                                // 重置 buffer 位置，不影响后续 Muxer 读取
                                outputBuffer.clear()
                                listener.onEncodedData(data, bufferInfo.flags, bufferInfo.presentationTimeUs)
                            }
                        }

                        // 写入 MediaMuxer（本地录像）
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            synchronized(encoderLock) {
                                if (muxerStarted && trackIndex >= 0) {
                                    mediaMuxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                                }
                            }
                        }

                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    else -> break
                }
            } catch (e: Exception) {
                Log.e(TAG, "编解码器异常，处理输出缓冲区失败", e)
                break
            }
        }
    }

    /**
     * 停止编码并写入结束标记
     */
    fun stop() {
        synchronized(encoderLock) {
            try {
                // 发送结束标记
                val inputBufferIndex = mediaCodec?.dequeueInputBuffer(TIMEOUT_US) ?: -1
                if (inputBufferIndex >= 0) {
                    mediaCodec?.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        0,
                        0,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // 处理剩余输出
                drainRemainingOutput()

            } catch (e: Exception) {
                Log.e(TAG, "停止编码器失败", e)
            }
        }
    }

    /**
     * 排空剩余输出
     */
    private fun drainRemainingOutput() {
        val bufferInfo = MediaCodec.BufferInfo()
        var outputDone = false

        while (!outputDone) {
            try {
                val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) ?: -1

                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        outputDone = true
                    }
                    outputBufferIndex >= 0 -> {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            synchronized(encoderLock) {
                                if (muxerStarted && trackIndex >= 0) {
                                    mediaMuxer?.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                                }
                            }
                        }
                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                    else -> outputDone = true
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "编解码器状态异常，排空剩余输出失败", e)
                outputDone = true
            }
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        synchronized(encoderLock) {
            try {
                // 移除监听器
                encodedDataListener = null

                mediaCodec?.stop()
                mediaCodec?.release()
                mediaCodec = null

                if (muxerStarted) {
                    mediaMuxer?.stop()
                }
                mediaMuxer?.release()
                mediaMuxer = null

                Log.i(TAG, "编码器资源释放完成")
            } catch (e: Exception) {
                Log.e(TAG, "释放编码器资源失败", e)
            }
        }
    }
}
