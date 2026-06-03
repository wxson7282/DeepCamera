package com.example.security_camera.streaming

import android.media.MediaCodec
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

class StreamWebSocketServer(port: Int = DEFAULT_PORT) :
    WebSocketServer(InetSocketAddress(port)) {

    companion object {
        private const val TAG = "StreamWS"
        const val DEFAULT_PORT = 8080
        const val FRAME_TYPE_CONFIG = 0x01
        const val FRAME_TYPE_KEY_FRAME = 0x02
        const val FRAME_TYPE_P_FRAME = 0x03
    }

    @Volatile
    private var cachedConfig: ConfigData? = null
    @Volatile
    private var cachedFormat: MediaFormat? = null
    @Volatile
    private var cachedCodecConfigData: ByteArray? = null
    @Volatile
    private var cachedCodecConfigPts: Long = 0

    @Volatile
    private var videoWidth: Int = 640
    @Volatile
    private var videoHeight: Int = 480
    @Volatile
    private var videoMime: String = "video/avc"

    private val clientLock = Any()
    var isServerRunning = false

    fun startServer() {
        try {
            start()
            Log.i(TAG, "WebSocket 服务器已启动，端口: $port")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket 服务器启动失败", e)
        }
    }

    fun stopServer() {
        try {
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

    fun getConnectedClientCount(): Int = connections.size

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "客户端连接: ${conn.remoteSocketAddress}")
        synchronized(clientLock) {
            val simpleConfig = """{
                "type":"config",
                "mime":"$videoMime",
                "width":$videoWidth,
                "height":$videoHeight
            }""".trimIndent()
            conn.send(simpleConfig)
            Log.i(TAG, "已发送简化配置 JSON")

            val configData = cachedCodecConfigData
            if (configData != null) {
                val frame = buildBinaryFrame(FRAME_TYPE_CONFIG, 0, configData)
                conn.send(frame)
                Log.i(TAG, "已发送 codec config 二进制帧")
            } else {
                Log.w(TAG, "⚠️ configData 还是 null，等第一帧出来再补发")
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        isServerRunning = false
        Log.i(TAG, "客户端断开: ${conn.remoteSocketAddress}, code=$code, reason=$reason")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        Log.d(TAG, "收到文本消息: $message")
    }

    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "WebSocket 错误", ex)
    }

    override fun onStart() {
        isServerRunning = true
        Log.i(TAG, "WebSocket 服务器已就绪")
    }

    fun onFormatChanged(format: MediaFormat) {
        synchronized(clientLock) {
            cachedFormat = format
            videoMime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                videoWidth = format.getInteger(MediaFormat.KEY_WIDTH)
            }
            if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                videoHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
            }

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
                mime = videoMime,
                width = videoWidth,
                height = videoHeight,
                csd0Base64 = csd0Base64,
                csd1Base64 = csd1Base64
            )

            val configJson = buildConfigJson()
            if (configJson != null) {
                broadcast(configJson)
                Log.d(TAG, "配置变更已广播")
            }
        }
    }

    fun onEncodedFrame(data: ByteArray, flags: Int, presentationTimeUs: Long) {
        // ★ 关键修复：即使没有客户端连接，也要先缓存 config 帧！
        Log.d(TAG, "onEncodedFrame, flags=$flags, presentationTimeUs=$presentationTimeUs, data size=${data.size}")
        val shouldBroadcast = connections.isNotEmpty()
        var frameType: Int

        when {
            flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 -> {
                cachedCodecConfigData = data
                cachedCodecConfigPts = presentationTimeUs
                frameType = FRAME_TYPE_CONFIG
                Log.i(TAG, "✅ 已缓存 codec config 帧，大小: ${data.size} bytes")
            }
            flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 -> {
                frameType = FRAME_TYPE_KEY_FRAME
                Log.d(TAG, "key frame，大小: ${data.size} bytes")
            }
            else -> {
                frameType = FRAME_TYPE_P_FRAME
                Log.d(TAG, "p frame，大小: ${data.size} bytes")
            }
        }

        if (shouldBroadcast) {
            val frame = buildBinaryFrame(frameType, presentationTimeUs, data)
            broadcast(frame)
            Log.d(TAG, "已广播 frame. frameType=$frameType ")
        }
    }

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

    private data class ConfigData(
        val mime: String,
        val width: Int,
        val height: Int,
        val csd0Base64: String,
        val csd1Base64: String
    )
}