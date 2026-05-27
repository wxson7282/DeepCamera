package com.example.security_camera.streaming

import android.media.MediaFormat
import android.util.Base64
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 视频流 WebSocket 服务器
 *
 * 协议设计：
 * ────────────────────────────────────────────────────
 * 文本消息（JSON）—— 编码器配置
 *   {"type":"config","mime":"video/avc","width":640,"height":480,
 *    "csd0":"<base64>","csd1":"<base64>"}
 *
 * 二进制消息 —— 编码帧数据
 *   Byte 0:    帧类型 (0x01=Config, 0x02=KeyFrame, 0x03=PFrame)
 *   Byte 1-8:  presentationTimeUs (big-endian int64)
 *   Byte 9+:   NAL 单元数据
 * ────────────────────────────────────────────────────
 *
 * 新客户端连接时，自动发送缓存的编码器配置，
 * 然后持续推送后续编码帧。
 */
class StreamWebSocketServer(port: Int = DEFAULT_PORT) :
    WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "StreamWS"
        const val DEFAULT_PORT = 8080

        // 二进制帧类型
        const val FRAME_TYPE_CONFIG = 0x01
        const val FRAME_TYPE_KEY_FRAME = 0x02
        const val FRAME_TYPE_P_FRAME = 0x03
    }

    // 缓存的编码器配置，新客户端连接时发送
    @Volatile
    private var cachedConfig: ConfigData? = null

    // 缓存的编码器格式（用于生成 config JSON）
    @Volatile
    private var cachedFormat: MediaFormat? = null

    // 缓存的 SPS/PPS NAL 数据（带 CODEC_CONFIG 标志的帧）
    @Volatile
    private var cachedCodecConfigData: ByteArray? = null
    @Volatile
    private var cachedCodecConfigPts: Long = 0

    private val clientLock = Any()

    var isServerRunning = false

    // ==================== 生命周期 ====================

    fun startServer() {
        try {
            start()
            Log.i(TAG, "WebSocket 服务器已启动，端口: ${port}")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket 服务器启动失败", e)
        }
    }

    fun stopServer() {
        try {
            // 关闭所有客户端连接
            synchronized(clientLock) {
                connections.forEach { conn ->
                    try {
                        conn.close(1000, "Server shutting down")
                    } catch (_: Exception) {
                    }
                }
            }
            stop()
            Log.i(TAG, "WebSocket 服务器已停止")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket 服务器停止失败", e)
        }
    }

//    fun isServerRunning(): Boolean = StreamManager.getInstance().isRunning()
//    fun isServerRunning() = this.isOpen || this.isStarting

    fun getConnectedClientCount(): Int = connections.size

    // ==================== WebSocket 回调 ====================

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "客户端连接: ${conn.remoteSocketAddress}")

        // 向新客户端发送缓存的编码器配置
        synchronized(clientLock) {
            // 发送 JSON 配置
            val configJson = buildConfigJson()
            if (configJson != null) {
                conn.send(configJson)
                Log.d(TAG, "已发送配置给新客户端")
            }

            // 发送缓存的 codec config 帧（SPS/PPS NAL）
            val configData = cachedCodecConfigData
            if (configData != null) {
                val frame = buildBinaryFrame(
                    FRAME_TYPE_CONFIG,
                    cachedCodecConfigPts,
                    configData
                )
                conn.send(frame)
                Log.d(TAG, "已发送 codec config 帧给新客户端")
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        isServerRunning = false
        Log.i(TAG, "客户端断开: ${conn.remoteSocketAddress}, code=$code, reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // 客户端文本消息（预留，可用于控制指令）
        Log.d(TAG, "收到文本消息: $message")
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        // 客户端二进制消息（预留）
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket 错误", ex)
    }

    override fun onStart() {
        isServerRunning = true
        Log.i(TAG, "WebSocket 服务器已就绪")
    }

    // ==================== 编码数据接口（由 StreamManager 调用） ====================

    /**
     * 编码器格式变化时调用
     */
    fun onFormatChanged(format: MediaFormat) {
        synchronized(clientLock) {
            cachedFormat = format

            // 提取 csd 数据
            val csd0 = format.getByteBuffer("csd-0")
            val csd1 = format.getByteBuffer("csd-1")

            val csd0Base64 = csd0?.let {
                val bytes = ByteArray(it.remaining())
                it.duplicate().get(bytes)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""

            val csd1Base64 = csd1?.let {
                val bytes = ByteArray(it.remaining())
                it.duplicate().get(bytes)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } ?: ""

            cachedConfig = ConfigData(
                mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc",
                width = format.getInteger(MediaFormat.KEY_WIDTH),
                height = format.getInteger(MediaFormat.KEY_HEIGHT),
                csd0Base64 = csd0Base64,
                csd1Base64 = csd1Base64
            )

            // 向所有已连接客户端推送配置变更
            val configJson = buildConfigJson()
            if (configJson != null) {
                broadcast(configJson)
                Log.d(TAG, "配置变更已广播")
            }
        }
    }

    /**
     * 编码帧数据到达时调用
     */
    fun onEncodedFrame(data: ByteArray, flags: Int, presentationTimeUs: Long) {
        if (connections.isEmpty()) return

        val frameType = when {
            flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> {
                // 缓存 codec config 帧
                cachedCodecConfigData = data
                cachedCodecConfigPts = presentationTimeUs
                FRAME_TYPE_CONFIG
            }
            flags and android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 -> {
                FRAME_TYPE_KEY_FRAME
            }
            else -> {
                FRAME_TYPE_P_FRAME
            }
        }

        val frame = buildBinaryFrame(frameType, presentationTimeUs, data)
        broadcast(frame)
    }

    // ==================== 内部方法 ====================

    private fun buildConfigJson(): String? {
        val config = cachedConfig ?: return null
        return JSONObject().apply {
            put("type", "config")
            put("mime", config.mime)
            put("width", config.width)
            put("height", config.height)
            put("csd0", config.csd0Base64)
            put("csd1", config.csd1Base64)
        }.toString()
    }

    /**
     * 构建二进制帧：
     * [1 byte frameType] [8 bytes pts big-endian] [NAL data]
     */
    private fun buildBinaryFrame(frameType: Int, pts: Long, data: ByteArray): ByteBuffer {
        val frameSize = 1 + 8 + data.size
        val buffer = ByteBuffer.allocate(frameSize)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(frameType.toByte())
        buffer.putLong(pts)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    // ==================== 数据类 ====================

    private data class ConfigData(
        val mime: String,
        val width: Int,
        val height: Int,
        val csd0Base64: String,
        val csd1Base64: String
    )
}
