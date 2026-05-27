package com.example.security_camera

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.security_camera.camera_manager.MyCameraManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityCameraViewModel : ViewModel() {
    private val _viewState: MutableState<ViewState> = mutableStateOf(ViewState())
    val viewState: State<ViewState> = _viewState
    var myCameraManager: MyCameraManager? = null

    data class ViewState(
        val isVideoRecoding: Boolean = false,
        val isScreenOn: Boolean = false,
        val isStreaming: Boolean = false,
        val streamClientCount: Int = 0
    )

    fun dispatch(action: Action) =
        reduce(viewState.value, action)

    private fun reduce(state: ViewState, action: Action) {
        Log.i("SecurityCameraViewModel", "reduce() is going. action = $action")
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                emit(
                    when (action) {
                        Action.StartRecord -> run {
                            myCameraManager?.startRecord()
                            state.copy(isVideoRecoding = true)
                        }
                        Action.StopRecord -> run {
                            myCameraManager?.stopRecord()
                            state.copy(isVideoRecoding = false)
                        }
                        Action.TurnOnScreen -> run {
                            state.copy(isScreenOn = true)
                        }
                        Action.TurnOffScreen -> run {
                            state.copy(isScreenOn = false)
                        }
                        Action.StartStream -> run {
                            myCameraManager?.startStreaming()
                            state.copy(isStreaming = true)
                        }
                        Action.StopStream -> run {
                            myCameraManager?.stopStreaming()
                            state.copy(isStreaming = false, streamClientCount = 0)
                        }
                    },
                )
            }
        }
    }

    /**
     * 更新流客户端数量（由 UI 定时轮询调用）
     */
    fun updateStreamClientCount() {
        val count = myCameraManager?.getStreamClientCount() ?: 0
        if (_viewState.value.streamClientCount != count) {
            _viewState.value = _viewState.value.copy(streamClientCount = count)
        }
    }

    private fun emit(state: ViewState) {
        _viewState.value = state
    }
}

sealed interface Action {
    object StartRecord : Action
    object StopRecord : Action
    object TurnOffScreen : Action
    object TurnOnScreen : Action
    object StartStream : Action
    object StopStream : Action
}