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

@Composable
fun ConcurrentCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Create PreviewViews for both cameras
    val frontPreviewView = remember { PreviewView(context) }
    val backPreviewView = remember { PreviewView(context) }
    // Initialize camera provider and cameras
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var concurrentCamera by remember { mutableStateOf<ConcurrentCamera?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        // Wait for the camera provider to be available
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        val cameraProvider = cameraProvider ?: return@DisposableEffect onDispose {}
        val frontCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        val backCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        val frontPreview = Preview.Builder().setTargetResolution(Size(640, 480)).build()
            .also { it.setSurfaceProvider(frontPreviewView.surfaceProvider) }
        val backPreview = Preview.Builder().setTargetResolution(Size(640, 480)).build()
            .also { it.setSurfaceProvider(backPreviewView.surfaceProvider) }
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
            cameraProvider.unbindAll()
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