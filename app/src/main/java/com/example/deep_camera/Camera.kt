package com.example.deep_camera

import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun Modifier.Camera() {
    // 在这里编写您的屏幕内容
    Log.i("Camera", "Start")
    CameraPreview()
}

@OptIn(ExperimentalCameraProviderConfiguration::class)
@Composable
private fun CameraPreview() {
    // 在这里编写您的预览内容
    val context = LocalContext.current
    val cameraController = LifecycleCameraController(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FIT_CENTER    // 视频帧位于中心，无拉伸变形
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE  // SurfaceView 模式
                setBackgroundColor(Color.Gray.toArgb())
            }.also { previewView ->
                previewView.controller = cameraController
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        },
        onReset = {
            Log.i("Camera", "Reset")
        },
        onRelease = {
            Log.i("Camera", "Release")
            cameraController.unbind()
        }
    ) {
        Log.i("Camera", "Update")
    }
}
