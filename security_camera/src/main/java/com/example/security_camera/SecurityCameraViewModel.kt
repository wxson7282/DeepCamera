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
        val btnVideoCaptureIsOn: Boolean = false
    )

    fun dispatch(action: Action) =
        reduce(viewState.value, action)

    private fun reduce(state: ViewState, action: Action) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) {
                when (action) {
                    Action.Start -> {
                        // TODO: 2023/08/10 启动视频捕获
                        _viewState.value = state.copy(btnVideoCaptureIsOn = true)
                    }
                    Action.Stop -> {
                        // TODO: 2023/08/10 停止视频捕获
                        _viewState.value = state.copy(btnVideoCaptureIsOn = false)
                    }
                }
            }
        }
    }
}

sealed interface Action {
    object Start : Action
    object Stop : Action
}