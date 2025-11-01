package com.example.security_camera

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainSurface(
    modifier: Modifier = Modifier,
    sharedPreferences: SharedPreferences
) {
    Log.i("MainSurface", "MainSurface start")
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel = viewModel<SecurityCameraViewModel>()
    val viewState = viewModel.viewState.value
    // 初始化相机管理器
    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            sharedPreferences = sharedPreferences
        )
    }

    // 相机初始化
    LaunchedEffect(Unit) {
        cameraManager.initCamera()
    }

    // 生命周期管理
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraBody(
            modifier = Modifier.fillMaxSize(),
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
                }
            ),
            sharedPreferences = sharedPreferences,
            previewView = cameraManager.previewView
        )
    }
}

data class Clickable constructor(
    val onRecordBtnPressed : () -> Unit,
    val onScreenOffBtnPressed : () -> Unit
)

fun combinedClickable(
    onRecordBtnPressed : () -> Unit = {},
    onScreenOffBtnPressed : () -> Unit = {}
) = Clickable(onRecordBtnPressed, onScreenOffBtnPressed)
