package com.example.security_camera

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.security_camera.camera_manager.MyCameraManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSurface(
    navController: NavController,
    myCameraManager: MyCameraManager
) {
    val logTag = "MainSurface"
    Log.i(logTag, "MainSurface start")
    val viewModel = viewModel<SecurityCameraViewModel>()
    viewModel.myCameraManager = myCameraManager
    val viewState = viewModel.viewState.value

    // 启动异步任务
    LaunchedEffect(Unit) {
        myCameraManager.initCamera()
        Log.i(logTag, "cameraManager.initCamera()")
    }

    // ★ 定时更新流客户端数量
    LaunchedEffect(viewState.isStreaming) {
        while (viewState.isStreaming) {
            viewModel.updateStreamClientCount()
            kotlinx.coroutines.delay(2000)
        }
    }

    // 生命周期管理
    DisposableEffect(Unit) {
        onDispose {
            myCameraManager.release()
            Log.i(logTag, "cameraManager.release()")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Security Camera" +
                            if (viewState.isStreaming) " · ${viewState.streamClientCount}个客户端" else "")
                },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                        onClick = { navigateToSettings(navController) }) {
                        Icon(imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings")
                    }
                }
            )
        },
    ) { paddingValues ->
        CameraBody(
            modifier = Modifier.padding(paddingValues),
            clickable = combinedClickable(
                onRecordBtnPressed = {
                    if (!viewState.isVideoRecoding) {
                        viewModel.dispatch(Action.StartRecord)
                    } else {
                        viewModel.dispatch(Action.StopRecord)
                    }
                },
                onScreenOffBtnPressed = {
                    if (!viewState.isScreenOn) {
                        viewModel.dispatch(Action.TurnOnScreen)
                    } else {
                        viewModel.dispatch(Action.TurnOffScreen)
                    }
                },
                onStreamBtnPressed = {
                    if (!viewState.isStreaming) {
                        viewModel.dispatch(Action.StartStream)
                    } else {
                        viewModel.dispatch(Action.StopStream)
                    }
                }
            ),
            previewView = myCameraManager.previewView
        )
    }
}

data class Clickable(
    val onRecordBtnPressed: () -> Unit,
    val onScreenOffBtnPressed: () -> Unit,
    val onStreamBtnPressed: () -> Unit = {}
)

fun combinedClickable(
    onRecordBtnPressed: () -> Unit = {},
    onScreenOffBtnPressed: () -> Unit = {},
    onStreamBtnPressed: () -> Unit = {}
) = Clickable(onRecordBtnPressed, onScreenOffBtnPressed, onStreamBtnPressed)

private fun navigateToSettings(navController: NavController) {
    navController.navigate("settings") {
        popUpTo("main") {
            inclusive = true
        }
    }
}
