package com.example.dual_camera

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Suppress("DEPRECATION")
@Composable
fun ConcurrentCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 建立PreviewView
    val frontPreviewView = remember { PreviewView(context) }
    val backPreviewView = remember { PreviewView(context) }
    // 初始化CameraProvider的监听器
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    // 初始化CameraProvider和ConcurrentCamera
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var concurrentCamera by remember { mutableStateOf<ConcurrentCamera?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        // 监听CameraProvider的变化，从监听器中获取CameraProvider实例
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        // 如果CameraProvider为空，则不执行任何操作
        val cameraProvider = cameraProvider ?: return@DisposableEffect onDispose {}
        //定义前后摄像头的选择器
        val frontCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val backCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        // 创建前后摄像头的预览用例，并将其绑定到对应的PreviewView上
        val frontPreview = Preview.Builder().setTargetResolution(Size(640, 480)).build()
            .also { it.setSurfaceProvider(frontPreviewView.surfaceProvider) }
        val backPreview = Preview.Builder().setTargetResolution(Size(640, 480)).build()
            .also { it.setSurfaceProvider(backPreviewView.surfaceProvider) }
        // 创建前后摄像头的配置
        val frontSingleCameraConfig = SingleCameraConfig(
            frontCameraSelector,
            UseCaseGroup.Builder().addUseCase(frontPreview).build(),
            lifecycleOwner
        )
        val backSingleCameraConfig = SingleCameraConfig(
            backCameraSelector,
            UseCaseGroup.Builder().addUseCase(backPreview).build(),
            lifecycleOwner
        )
        try {
            // 解除既有绑定
            cameraProvider.unbindAll()
            // 绑定前后摄像头的配置，建立concurrentCamera
            cameraProvider.bindToLifecycle(
                listOf(
                    frontSingleCameraConfig, backSingleCameraConfig
                )
            )
        } catch (e: Exception) {
            Log.e("ConcurrentCameraScreen", "Failed to bind cameras", e)
            e.printStackTrace()
        }
        onDispose {
            concurrentCamera = null
            cameraProvider.unbindAll()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { frontPreviewView },
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
        )
        AndroidView(
            factory = { backPreviewView },
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterHorizontally)
        )
    }
}