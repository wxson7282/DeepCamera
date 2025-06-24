package com.example.dual_camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun DualCameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.weight(1f),
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
            context = context,
            lifecycleOwner = lifecycleOwner
        )
        CameraPreview(
            modifier = Modifier.weight(1f),
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            context = context,
            lifecycleOwner = lifecycleOwner
        )
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector,
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        val cameraProvider = cameraProvider ?: return@DisposableEffect onDispose {}

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}