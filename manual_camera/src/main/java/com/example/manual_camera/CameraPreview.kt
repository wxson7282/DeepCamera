package com.example.manual_camera

import android.content.Context
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun Modifier.CameraPreview(
    context: Context,
    zoomRatio: Float,
    focusDistance: Float
) {
    Log.i("CameraPreview", "Start")
    val cameraController = LifecycleCameraController(context)
    val lifecycleOwner = LocalLifecycleOwner.current
    // 获取 Camera2CameraControl
//    val camera2CameraControl = Util.getCamera2CameraControl(context, lifecycleOwner)
    // 监听缩放比例的变化
    LaunchedEffect(zoomRatio) {
        // 设置新的缩放比例
        cameraController.setZoomRatio(zoomRatio)
        Log.i("CameraPreview", "ZoomRatio: $zoomRatio")
    }
//    // 监听焦距的变化
//    LaunchedEffect(focusDistance) {
//        if (camera2CameraControl == null) {
//            Log.e("CameraPreview", "Camera2CameraControl is null")
//            return@LaunchedEffect
//        }
//        val captureRequestOptions = CaptureRequestOptions.Builder()
//            .setCaptureRequestOption(
//                CaptureRequest.CONTROL_AF_MODE,
//                CameraMetadata.CONTROL_AF_MODE_OFF)
//            .setCaptureRequestOption(
//                CaptureRequest.LENS_FOCUS_DISTANCE,
//                focusDistance)
//            .build()
//        camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
//        Log.i("CameraPreview", "FocusDistance: $focusDistance")
//    }
    AndroidView(
        modifier = this,
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FIT_START
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE  // SurfaceView 模式
                setBackgroundColor(Color.Gray.toArgb())
            } .also { previewView ->
                previewView.controller = cameraController
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        },
        onReset = {
            Log.i("CameraPreview", "Reset")
        },
        onRelease = {
            Log.i("CameraPreview", "Release")
            cameraController.unbind()
        }
    ) {
        Log.i("CameraPreview", "Update")
    }
}