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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
@Composable
fun ConcurrentCameraScreen(
    shutterSound: ShutterSound? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // 定义前后按钮是否可见
    var visibleFront by remember { mutableStateOf(true) }
    var visibleBack by remember { mutableStateOf(true) }
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
    // 使用ConstraintLayout布局，将前后摄像头的预览和按钮放置在合适的位置上
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (frontPreviewRef, backPreviewRef, frontButtonRef, backButtonRef) = createRefs()
        val scope = rememberCoroutineScope()
        // 定义前摄像头的预览是否可见
        AnimatedVisibility(
            visible = visibleFront,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.constrainAs(frontPreviewRef) {
                top.linkTo(parent.top, 145.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
            AndroidView(factory = { frontPreviewView })
        }
        // 定义后摄像头的预览是否可见
        AnimatedVisibility(
            visible = visibleBack,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.constrainAs(backPreviewRef) {
                bottom.linkTo(parent.bottom, 125.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
            AndroidView(factory = { backPreviewView })
        }
        // 定义前摄像头的按钮
        IconButton(modifier = Modifier.constrainAs(frontButtonRef) {
            top.linkTo(frontPreviewRef.bottom, 30.dp)
            start.linkTo(frontPreviewRef.start)
            end.linkTo(frontPreviewRef.end)
        }, onClick = {
            // 点击前摄像头按钮时，将前摄像头的预览设置为不可见
            visibleFront = false
            // 拍照
            Util.takePicture(context, frontImageCapture, shutterSound, isFront = true)
            scope.launch {
                delay(300)
                // 拍照完成后，将前摄像头的预览设置为可见
                visibleFront = true
            }
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
            // 点击后摄像头按钮时，将后摄像头的预览设置为不可见
            visibleBack = false
            // 拍照
            Util.takePicture(context, backImageCapture, shutterSound, isFront = false)
            scope.launch {
                delay(300)
                // 拍照完成后，将后摄像头的预览设置为可见
                visibleBack = true
            }
        }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.circle),
                tint = Color.Unspecified,
                contentDescription = "Shutter"
            )
        }
    }
}

