package com.example.security_monitor

import android.util.Log
import android.view.SurfaceHolder
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.example.security_monitor.streaming.StreamClient
import com.example.security_monitor.streaming.StreamDecoder

class StreamViewModel : ViewModel() {

    private val _viewState = mutableStateOf(ViewState())
    val viewState: State<ViewState> = _viewState

    private var streamClient: StreamClient? = null
    private var streamDecoder: StreamDecoder? = null

    data class ViewState(
        val serverAddress: String = "",
        val isConnected: Boolean = false,
        val errorMessage: String? = null,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val codecName: String = "",
        val frameCount: Int = 0
    )

    fun updateServerAddress(address: String) {
        _viewState.value = _viewState.value.copy(serverAddress = address)
    }

    /**
     * 连接到服务器
     */
    fun connect(address: String) {
        if (address.isBlank()) {
            _viewState.value = _viewState.value.copy(errorMessage = "请输入服务器地址")
            return
        }

        // 构造 WebSocket URL
        val wsUrl = buildWsUrl(address)

        // 创建解码器
        streamDecoder = StreamDecoder()

        // 创建客户端
        streamClient = StreamClient(
            wsUrl = wsUrl,
            onConfigReceived = { mime, width, height, csd0, csd1 ->
                Log.i(TAG, "收到配置: $mime ${width}x${height}")
                _viewState.value = _viewState.value.copy(
                    videoWidth = width,
                    videoHeight = height,
                    codecName = if (mime.contains("HEVC", ignoreCase = true)) "H265" else "H264"
                )
                // 配置解码器
                streamDecoder?.configure(mime, width, height, csd0, csd1)
            },
            onFrameReceived = { data, frameType, pts ->
                // 喂数据给解码器
                streamDecoder?.feedData(data, frameType, pts)
                // 更新帧计数
                val count = _viewState.value.frameCount + 1
                _viewState.value = _viewState.value.copy(frameCount = count)
            },
            onConnectionChanged = { connected ->
                _viewState.value = _viewState.value.copy(
                    isConnected = connected,
                    errorMessage = if (!connected) "连接已断开" else null,
                    frameCount = 0
                )
            },
            onError = { error ->
                _viewState.value = _viewState.value.copy(
                    errorMessage = error,
                    isConnected = false
                )
            }
        )

        streamClient?.connect()

        _viewState.value = _viewState.value.copy(
            errorMessage = null
        )
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        streamClient?.disconnect()
        streamClient = null
        streamDecoder?.release()
        streamDecoder = null
        _viewState.value = _viewState.value.copy(
            isConnected = false,
            errorMessage = null,
            videoWidth = 0,
            videoHeight = 0,
            codecName = "",
            frameCount = 0
        )
    }

    /**
     * 初始化解码器 Surface
     */
    fun initDecoder(surfaceHolder: SurfaceHolder) {
        streamDecoder?.setSurface(surfaceHolder.surface)
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }

    private fun buildWsUrl(address: String): String {
        // 支持用户输入 "192.168.1.100:8080" 或 "192.168.1.100"
        val trimmed = address.trim()
        return if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) {
            trimmed
        } else {
            "ws://$trimmed/live"
        }
    }

    companion object {
        private const val TAG = "StreamViewModel"
    }
}
