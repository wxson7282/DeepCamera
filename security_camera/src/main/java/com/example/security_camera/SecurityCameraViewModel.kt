package com.example.security_camera

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SecurityCameraViewModel : ViewModel() {
    private val _viewState: MutableState<ViewState> = mutableStateOf(ViewState())
    val viewState : State<ViewState> = _viewState

    data class ViewState (
        val isVideoRecoding: Boolean = false,
        val isScreenOn: Boolean = false
    )

    fun dispatch(action: Action) =
        reduce(viewState.value, action)

    private fun reduce(state: ViewState, action: Action) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                when (action) {
                    Action.StartRecord -> {
                        // TODO: 2023/08/10 启动视频捕获
                        _viewState.value = state.copy(isVideoRecoding = true)
                    }
                    Action.StopRecord -> {
                        // TODO: 2023/08/10 停止视频捕获
                        _viewState.value = state.copy(isVideoRecoding = false)
                    }
                    Action.TurnOnScreen -> {
                        // TODO: 2023/08/10 打开屏幕
                        _viewState.value = state.copy(isScreenOn = true)
                    }
                    Action.TurnOffScreen -> {
                        // TODO: 2023/08/10 关闭屏幕
                        _viewState.value = state.copy(isScreenOn = false)
                    }
                }
            }
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
}