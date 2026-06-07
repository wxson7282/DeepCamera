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
 * 为了支持多种流传输和文件输出的组合场景，需要对编码器进行模块化设计，
 * 核心是将编码器核心（MediaCodec） 与两个输出目标（网络流/本地文件） 的生命周期解耦，
 * 使它们能独立启动/停止。
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
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val bitRate: Int,
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

    // 新增状态跟踪变量
    private var isStreamActive = false // 流传输是否活跃
    private var isFileOutputActive = false // 文件输出是否活跃
    private var isCodecStarted = false // 编码器是否已启动
    private var outputFormat: MediaFormat? = null // 缓存编码器输出格式，用于文件输出动态启动


    init {
        initEncoder()
    }

    // ==================== 监听器 ====================

    /**
     * 设置编码数据监听器（网络流）
     */
    fun setEncodedDataListener(listener: OnEncodedDataListener?) {
        val newStreamActive = listener != null
        if (newStreamActive != isStreamActive) {
            encodedDataListener = listener
            isStreamActive = newStreamActive
            Log.i(TAG, "编码数据监听器: ${if (listener != null) "已设置" else "已移除"}")
            updateEncoderState() // 检查是否需要启动/停止编码器
        }
    }

    /**
     * 启动文件输出（本地录像）
     * @param outputFile 输出文件
     */
    fun startFileOutput(outputFile: File) {
        synchronized(encoderLock) {
            if (isFileOutputActive) {
                Log.w(TAG, "文件输出已在运行中，先停止当前输出")
                stopFileOutput()
            }
            initMuxer(outputFile) // 动态初始化Muxer
            isFileOutputActive = true
            Log.i(TAG, "文件输出已启动: ${outputFile.absolutePath}")
            updateEncoderState() // 检查是否需要启动编码器

            // 如果已获取输出格式，立即配置Muxer
            outputFormat?.let { format ->
                trackIndex = mediaMuxer?.addTrack(format) ?: -1
                mediaMuxer?.start()
                muxerStarted = true
            }
        }
    }

    /**
     * 停止文件输出（本地录像）
     */
    fun stopFileOutput() {
        synchronized(encoderLock) {
            if (!isFileOutputActive) return
            try {
                if (muxerStarted) {
                    mediaMuxer?.stop()
                    muxerStarted = false
                }
                mediaMuxer?.release()
                mediaMuxer = null
                trackIndex = -1
                isFileOutputActive = false
                Log.i(TAG, "文件输出已停止")
                updateEncoderState() // 检查是否需要停止编码器
            } catch (e: Exception) {
                Log.e(TAG, "停止文件输出失败", e)
            }
        }
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

            // 创建配置编码器
            mediaCodec = MediaCodec.createEncoderByType(mimeType).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }

            Log.i(TAG, "编码器初始化成功: ${width}x${height} @ ${frameRate}fps, bitrate=$bitRate")

        } catch (e: Exception) {
            Log.e(TAG, "编码器初始化失败", e)
        }
    }

    /**
     * 初始化 MediaMuxer 封装器
     */
    private fun initMuxer(outputFile: File) {
        try {
            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mediaMuxer?.setOrientationHint(90) // 顺时针旋转 90 度
            Log.i(TAG, "Muxer 初始化成功: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Muxer 初始化失败", e)
        }
    }

    /**
     * 更新编码器状态（根据输出目标活跃度启停）
     */
    private fun updateEncoderState() {
        val shouldBeActive = isStreamActive || isFileOutputActive
        if (shouldBeActive && !isCodecStarted) {
            startEncoder()
        } else if (!shouldBeActive && isCodecStarted) {
            stopEncoder()
        }
    }

    /**
     * 启动编码器核心
     */
    private fun startEncoder() {
        if (mediaCodec == null) {
            initEncoder() // 初始化并启动MediaCodec
        }
        // 确保编码器只启动一次
        if (!isCodecStarted && mediaCodec != null) {
            try {
                mediaCodec?.start()
                isCodecStarted = true
                Log.i(TAG, "编码器已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动编码器失败", e)
                mediaCodec?.stop()
                mediaCodec?.release()
                mediaCodec = null
                isCodecStarted = false
            }

        }
    }

    /**
     * 停止编码器核心
     */
    private fun stopEncoder() {
        try {
            // 发送结束标记并处理剩余数据
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
            drainRemainingOutput()
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            isCodecStarted = false
            Log.i(TAG, "编码器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止编码器失败", e)
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
        Log.d(TAG, "encodeFrame start, watermarkedYuv size=${watermarkedYuv.size}, presentationTimeNs=$presentationTimeNs")
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
                Log.e(TAG, "编码帧失败 watermarkedYuv size=${watermarkedYuv.size} isStreamActive=$isStreamActive isFileOutputActive=$isFileOutputActive isCodecStarted=$isCodecStarted", e)
            }
        }
    }

    /**
     * 处理编码器输出缓冲区
     *
     * ★ 改造点：输出数据同时发给 MediaMuxer 和 OnEncodedDataListener
     */
    private fun processOutputBuffer() {
        Log.d(TAG, "processOutputBuffer start")
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(TAG, "编码器输出格式改变: $newFormat")
                        outputFormat = newFormat // 缓存格式用于后续文件输出启动

                        // 通知流监听器格式变化
                        encodedDataListener?.onFormatChanged(newFormat)

                        // 如果文件输出活跃，立即配置Muxer
                        synchronized(encoderLock) {
                            if (isFileOutputActive && !muxerStarted) {
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

                        // 发送数据到网络流（如果活跃）
                        encodedDataListener?.let { listener ->
                            if (bufferInfo.size > 0) {
                                val data = ByteArray(bufferInfo.size)
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                outputBuffer.get(data)
                                outputBuffer.clear()
                                listener.onEncodedData(data, bufferInfo.flags, bufferInfo.presentationTimeUs)
                                Log.d(TAG, "onEncodedData, data发送给webSocketServer, size=${bufferInfo.size}")
                            }
                        }

                        // 写入数据到本地文件（如果活跃）
                        if (isFileOutputActive && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
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
                Log.e(TAG, "处理输出缓冲区失败", e)
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
                // 停止所有输出
                stopFileOutput()
                encodedDataListener = null
                isStreamActive = false

                // 释放编码器
                mediaCodec?.stop()
                mediaCodec?.release()
                mediaCodec = null
                isCodecStarted = false

                Log.i(TAG, "编码器资源释放完成")
            } catch (e: Exception) {
                Log.e(TAG, "释放编码器资源失败", e)
            }
        }
    }


}
