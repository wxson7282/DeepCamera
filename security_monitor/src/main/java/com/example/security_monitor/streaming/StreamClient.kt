package com.example.security_monitor.streaming

import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * WebSocket 视频流客户端
 *
 * 连接到服务端的 WebSocket 服务器，接收编码器配置和帧数据。
 *
 * 协议：
 * - 文本消息（JSON）：编码器配置
 *   {"type":"config","mime":"video/avc","width":640,"height":480,"csd0":"<base64>","csd1":"<base64>"}
 *
 * - 二进制消息：编码帧
 *   Byte 0: 帧类型 (0x01=Config, 0x02=KeyFrame, 0x03=PFrame)
 *   Byte 1-8: presentationTimeUs (big-endian int64)
 *   Byte 9+: NAL 单元数据
 */
class StreamClient(
    private val wsUrl: String,
    private val onConfigReceived: (mime: String, width: Int, height: Int, csd0: ByteArray, csd1: ByteArray) -> Unit,
    private val onFrameReceived: (data: ByteArray, frameType: Int, pts: Long) -> Unit,
    private val onConnectionChanged: (connected: Boolean) -> Unit,
    private val onError: (error: String) -> Unit
) {
    companion object {
        private const val TAG = "StreamClient"
        const val FRAME_TYPE_CONFIG = 0x01
        const val FRAME_TYPE_KEY_FRAME = 0x02
//        const val FRAME_TYPE_P_FRAME = 0x03
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)  // WebSocket 长连接不超时
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    fun connect() {
        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket 连接成功: $wsUrl")
                isConnected = true
                onConnectionChanged(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    Log.i(TAG, "收到文本消息: $text")
                    handleTextMessage(text)
                } catch (e: Exception) {
                    Log.e(TAG, "处理文本消息失败", e)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    Log.i(TAG, "收到二进制消息: ${bytes.size}")
                    handleBinaryMessage(bytes)
                } catch (e: Exception) {
                    Log.e(TAG, "处理二进制消息失败", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket 正在关闭: code=$code, reason=$reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket 已关闭: code=$code, reason=$reason")
                isConnected = false
                onConnectionChanged(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 连接失败", t)
                isConnected = false
                onConnectionChanged(false)
                onError("连接失败: ${t.message}")
            }
        })

        Log.i(TAG, "正在连接: $wsUrl")
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        onConnectionChanged(false)
        Log.i(TAG, "已断开连接")
    }

//    fun isConnected(): Boolean = isConnected

    // ==================== 消息处理 ====================

    private fun handleTextMessage(text: String) {
        val json = JSONObject(text)
        when (val type = json.optString("type")) {
            "config" -> {
                val mime = json.getString("mime")
                val width = json.getInt("width")
                val height = json.getInt("height")
                val csd0Base64 = json.optString("csd0", "")
                val csd1Base64 = json.optString("csd1", "")

                val csd0 = if (csd0Base64.isNotEmpty()) {
                    Base64.decode(csd0Base64, Base64.NO_WRAP)
                } else {
                    ByteArray(0)
                }

                val csd1 = if (csd1Base64.isNotEmpty()) {
                    Base64.decode(csd1Base64, Base64.NO_WRAP)
                } else {
                    ByteArray(0)
                }

                Log.i(TAG, "收到编码器配置: $mime ${width}x${height}")
                onConfigReceived(mime, width, height, csd0, csd1)
            }
            else -> {
                Log.w(TAG, "未知文本消息类型: $type")
            }
        }
    }

    private fun handleBinaryMessage(bytes: ByteString) {
        if (bytes.size < 9) return  // 最少需要 1 字节类型 + 8 字节时间戳

        val buffer = bytes.asByteBuffer()
        buffer.order(ByteOrder.BIG_ENDIAN)

        val frameType = buffer.get().toInt() and 0xFF
        val pts = buffer.long

        val nalData = ByteArray(buffer.remaining())
        buffer.get(nalData)

        onFrameReceived(nalData, frameType, pts)
    }
}
