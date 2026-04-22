package com.example.security_camera.camera_manager

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File

/**
 * 视频编码器模块
 *
 * 职责：
 * 1. MediaCodec 初始化和配置（H.264 编码）
 * 2. MediaMuxer 封装为 MP4
 * 3. YUV 帧数据编码
 * 4. 编码器生命周期管理
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
        const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
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

    init {
        initEncoder()
        initMuxer()
    }

    /**
     * 初始化 MediaCodec 编码器
     */
    private fun initEncoder() {
        try {
            // 创建 H.264 编码格式
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            // 创建并配置编码器
            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
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
    private fun initMuxer() {
        try {
            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            Log.i(TAG, "Muxer 初始化成功: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Muxer 初始化失败", e)
        }
    }

//    /**
//     * 获取编码器的输入 Surface
//     */
//    fun getInputSurface() = mediaCodec?.createInputSurface()

    /**
     * 编码单帧 YUV 数据
     *
     * @param yuvData YUV 原始数据（YUV420 格式）
     * @param presentationTimeNs 时间戳（纳秒）
     */
    @SuppressLint("WrongConstant")
    fun encodeFrame(yuvData: ByteArray, presentationTimeNs: Long) {
        synchronized(encoderLock) {
            val codec = mediaCodec ?: return

            try {
                // 获取输入缓冲区
                val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: return
                    inputBuffer.clear()

                    // 将 YUV 数据写入输入缓冲区
                    val ySize = width * height
                    val uvSize = ySize / 4

                    // Y plane
                    inputBuffer.put(yuvData, 0, ySize)
                    // U plane
                    inputBuffer.put(yuvData, ySize + uvSize, uvSize)
                    // V plane
                    inputBuffer.put(yuvData, ySize, uvSize)

                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvData.size,
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
     */
    private fun processOutputBuffer() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
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
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex) ?: continue

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
        }
    }

//    /**
//     * 刷新编码器缓冲区
//     */
//    fun flush() {
//        synchronized(encoderLock) {
//            try {
//                mediaCodec?.flush()
//            } catch (e: Exception) {
//                Log.e(TAG, "刷新编码器失败", e)
//            }
//        }
//    }

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
            }
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        synchronized(encoderLock) {
            try {
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
