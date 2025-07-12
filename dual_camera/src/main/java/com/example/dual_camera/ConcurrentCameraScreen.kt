package com.example.dual_camera

import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.ConcurrentCamera.SingleCameraConfig
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner

@Suppress("DEPRECATION")
@Composable
fun ConcurrentCameraScreen(
    shutterSound: ShutterSound? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 创建前后镜头的imageCapture用例
    val frontImageCapture = ImageCapture.Builder().build()
    val backImageCapture = ImageCapture.Builder().build()
    // 建立PreviewView
    val frontPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    val backPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
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
        // 创建前后摄像头的预览用例，并将其绑定到对应的PreviewView上，并设置分辨率，该分辨率决定了预览画面的大小。
        val frontPreview = Preview.Builder().setTargetResolution(Size(576, 720)).build()
            .also { it.setSurfaceProvider(frontPreviewView.surfaceProvider) }
        val backPreview = Preview.Builder().setTargetResolution(Size(576, 720)).build()
            .also { it.setSurfaceProvider(backPreviewView.surfaceProvider) }
        // 创建前后摄像头的配置
        val frontSingleCameraConfig = SingleCameraConfig(
            frontCameraSelector,
            UseCaseGroup.Builder().addUseCase(frontPreview).addUseCase(frontImageCapture).build(),
            lifecycleOwner
        )
        val backSingleCameraConfig = SingleCameraConfig(
            backCameraSelector,
            UseCaseGroup.Builder().addUseCase(backPreview).addUseCase(backImageCapture).build(),
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
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (frontPreviewRef, backPreviewRef, frontButtonRef, backButtonRef) = createRefs()
        AndroidView(
            factory = { frontPreviewView },
            modifier = Modifier.constrainAs(frontPreviewRef) {
                    top.linkTo(parent.top, 145.dp)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
        AndroidView(factory = { backPreviewView },
            modifier = Modifier.constrainAs(backPreviewRef) {
                bottom.linkTo(parent.bottom, 125.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            })
        IconButton(modifier = Modifier.constrainAs(frontButtonRef) {
            top.linkTo(frontPreviewRef.bottom, 30.dp)
            start.linkTo(frontPreviewRef.start)
            end.linkTo(frontPreviewRef.end)
        }, onClick = {
            Util.takePicture(context, frontImageCapture, shutterSound, isFront = true)
        }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.circle),
                tint = Color.Unspecified,
                contentDescription = "Shutter"
            )
        }
        IconButton(modifier = Modifier.constrainAs(backButtonRef) {
            top.linkTo(backPreviewRef.bottom, 30.dp)
            start.linkTo(backPreviewRef.start)
            end.linkTo(backPreviewRef.end)
        }, onClick = {
            Util.takePicture(context, backImageCapture, shutterSound, isFront = false)
        }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.circle),
                tint = Color.Unspecified,
                contentDescription = "Shutter"
            )
        }
    }
}

