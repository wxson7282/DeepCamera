package com.example.security_monitor.streaming

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * 视频流解码器
 *
 * 接收 WebSocket 传来的编码数据，使用 MediaCodec 解码并渲染到 Surface。
 *
 * 生命周期：
 * 1. 收到 config → configure() 创建并配置解码器
 * 2. 设置 Surface → setSurface()
 * 3. 收到帧数据 → feedData() 解码并渲染
 * 4. 退出 → release() 释放资源
 *
 * 注意：configure 和 setSurface 的顺序——
 * 如果 Surface 已设置，configure 时直接传入；
 * 如果 Surface 后设置，需要重新 configure。
 */
class StreamDecoder {

    companion object {
        private const val TAG = "StreamDecoder"
        private const val TIMEOUT_US = 10000L
    }

    private var decoder: MediaCodec? = null
    private var surface: Surface? = null
    private var isConfigured = false
    private var isDecoding = false

    // 缓存配置参数，Surface 后设置时需要重新 configure
    private var cachedMime: String? = null
    private var cachedWidth: Int = 0
    private var cachedHeight: Int = 0
    private var cachedCsd0: ByteArray? = null
    private var cachedCsd1: ByteArray? = null

    // 解码线程
    private val decodeThread = Thread {
        drainOutputLoop()
    }

    @Volatile
    private var decodeThreadRunning = false

    /**
     * 配置解码器
     *
     * 在收到服务端编码器配置后调用。
     * 如果 Surface 已设置，直接启动解码；
     * 否则缓存参数，等 Surface 设置后再启动。
     */
    fun configure(mime: String, width: Int, height: Int, csd0: ByteArray, csd1: ByteArray) {
        // 缓存配置
        cachedMime = mime
        cachedWidth = width
        cachedHeight = height
        cachedCsd0 = csd0
        cachedCsd1 = csd1

        // 释放旧的解码器
        releaseDecoder()

        try {
            val format = MediaFormat.createVideoFormat(mime, width, height)

            // 设置 CSD（解码器必需的 SPS/PPS 信息）
            if (csd0.isNotEmpty()) {
                val csd0Buffer = ByteBuffer.allocate(csd0.size)
                csd0Buffer.put(csd0)
                csd0Buffer.flip()
                format.setByteBuffer("csd-0", csd0Buffer)
            }
            if (csd1.isNotEmpty()) {
                val csd1Buffer = ByteBuffer.allocate(csd1.size)
                csd1Buffer.put(csd1)
                csd1Buffer.flip()
                format.setByteBuffer("csd-1", csd1Buffer)
            }

            // 延迟多少帧都不报错
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, width * height * 3 / 2)

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(format, surface, null, 0)
                start()
            }

            isConfigured = true
            isDecoding = true

            // 启动输出轮询线程
            startDrainThread()

            Log.i(TAG, "解码器配置成功: $mime ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "解码器配置失败", e)
            isConfigured = false
        }
    }

    /**
     * 设置渲染 Surface
     *
     * 如果解码器已配置但还没设置 Surface，需要重新 configure。
     */
    fun setSurface(newSurface: Surface) {
        surface = newSurface

        // 如果已有配置但没有 Surface（或 Surface 变了），重新配置
        if (cachedMime != null && isConfigured) {
            val mime = cachedMime!!
            val w = cachedWidth
            val h = cachedHeight
            val c0 = cachedCsd0 ?: ByteArray(0)
            val c1 = cachedCsd1 ?: ByteArray(0)
            configure(mime, w, h, c0, c1)
            Log.i(TAG, "Surface 已设置，重新配置解码器")
        }
    }

    /**
     * 向解码器输入一帧编码数据
     *
     * @param data NAL 单元数据
     * @param frameType 帧类型（0x01=Config, 0x02=KeyFrame, 0x03=PFrame）
     * @param presentationTimeUs 展示时间戳（微秒）
     */
    fun feedData(data: ByteArray, frameType: Int, presentationTimeUs: Long) {
        val codec = decoder ?: return
        if (!isDecoding) return

        try {
            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(data)

                val flags = when (frameType) {
                    StreamClient.FRAME_TYPE_CONFIG -> MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                    StreamClient.FRAME_TYPE_KEY_FRAME -> MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }

                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    data.size,
                    presentationTimeUs,
                    flags
                )
                Log.i(TAG, "输入帧数据: ${data.size} 字节, 帧类型: $frameType, 展示时间戳: $presentationTimeUs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "输入帧数据失败", e)
        }
    }

    /**
     * 释放所有资源
     */
    fun release() {
        isDecoding = false
        decodeThreadRunning = false
        releaseDecoder()
        surface = null
        cachedMime = null
        cachedCsd0 = null
        cachedCsd1 = null
        Log.i(TAG, "解码器资源已释放")
    }

    // ==================== 内部方法 ====================

    private fun releaseDecoder() {
        try {
            isDecoding = false
            decodeThreadRunning = false

            decoder?.stop()
            decoder?.release()
            decoder = null
            isConfigured = false
        } catch (e: Exception) {
            Log.e(TAG, "释放解码器失败", e)
            decoder = null
            isConfigured = false
        }
    }

    private fun startDrainThread() {
        if (decodeThreadRunning) return
        decodeThreadRunning = true
        decodeThread.apply {
            if (!isAlive) {
                Thread({ drainOutputLoop() }, "StreamDecodeOutput").start()
            }
        }
    }

    /**
     * 输出轮询循环
     *
     * 在独立线程上持续从解码器读取解码后的帧并渲染到 Surface。
     */
    private fun drainOutputLoop() {
        val bufferInfo = MediaCodec.BufferInfo()

        while (decodeThreadRunning && isDecoding) {
            val codec = decoder ?: break

            try {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(TAG, "解码器输出格式: $newFormat")
                    }
                    outputIndex >= 0 -> {
                        if (surface != null && surface!!.isValid && isConfigured) {
                            // 渲染到 Surface（render=true）
                            codec.releaseOutputBuffer(outputIndex, true)
                            Log.i(TAG, "渲染帧数据: ${bufferInfo.size} 字节, 展示时间戳: ${bufferInfo.presentationTimeUs}")
                        } else {
                            Log.e(TAG, "Surface无效，无法渲染帧数据")
                            codec.releaseOutputBuffer(outputIndex, false)
                        }

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            Log.i(TAG, "解码器收到 EOS")
                            break
                        }
                    }
                    // INFO_TRY_AGAIN_LATER: 正常，继续轮询
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "解码器状态异常", e)
                break
            } catch (e: Exception) {
                Log.e(TAG, "输出轮询异常", e)
                break
            }
        }

        decodeThreadRunning = false
        Log.d(TAG, "输出轮询线程退出")
    }
}
