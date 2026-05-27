package com.example.security_camera.streaming

import android.media.MediaFormat
import android.util.Log

/**
 * 流媒体管理器（单例）
 *
 * 职责：
 * 1. 管理 StreamWebSocketServer 的生命周期
 * 2. 实现 OnEncodedDataListener，接收 VideoEncoder 的编码输出
 * 3. 缓存编码器配置，新客户端连接时可立即推送
 * 4. 协调 VideoStreamService 与 VideoEncoder 之间的数据流
 *
 * 使用方式：
 *   StreamManager.getInstance().start()    // 启动 WebSocket 服务器
 *   StreamManager.getInstance().stop()     // 停止
 *   videoEncoder.setEncodedDataListener(StreamManager.getInstance())
 */
class StreamManager private constructor() : OnEncodedDataListener {

    companion object {
        private const val TAG = "StreamManager"

        @Volatile
        private var instance: StreamManager? = null

        fun getInstance(): StreamManager {
            return instance ?: synchronized(this) {
                instance ?: StreamManager().also { instance = it }
            }
        }
    }

    private var webSocketServer: StreamWebSocketServer? = null

    @Volatile
    private var isStreaming = false

    // ==================== 生命周期 ====================

    /**
     * 启动流媒体服务
     *
     * @param port WebSocket 端口号，默认 8080
     */
    fun start(port: Int = StreamWebSocketServer.DEFAULT_PORT) {
        if (isStreaming) {
            Log.w(TAG, "流媒体服务已在运行")
            return
        }

        webSocketServer = StreamWebSocketServer(port).also {
            it.startServer()
        }
        isStreaming = true
        Log.i(TAG, "流媒体服务已启动，端口: $port")
    }

    /**
     * 停止流媒体服务
     */
    fun stop() {
        if (!isStreaming) {
            Log.w(TAG, "流媒体服务未运行")
            return
        }

        webSocketServer?.stopServer()
        webSocketServer = null
        isStreaming = false
        Log.i(TAG, "流媒体服务已停止")
    }

    fun isRunning(): Boolean = isStreaming && webSocketServer?.isServerRunning == true

    fun getConnectedClientCount(): Int = webSocketServer?.getConnectedClientCount() ?: 0

    // ==================== OnEncodedDataListener 实现 ====================

    override fun onFormatChanged(format: MediaFormat) {
        webSocketServer?.onFormatChanged(format)
    }

    override fun onEncodedData(data: ByteArray, flags: Int, presentationTimeUs: Long) {
        webSocketServer?.onEncodedFrame(data, flags, presentationTimeUs)
    }
}
