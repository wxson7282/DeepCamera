package com.example.manual_camera

import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.manual_camera.Util.takePicture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ManualCameraScreen(shutterSound: ShutterSound? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = LifecycleCameraController(context)
    val minFocusDistance = Util.getMinFocusDistance(context)
    var stateOfFocusDistance by remember { mutableFloatStateOf(minFocusDistance) }
    // 定义previewView是否可见
    var stateOfViewIsVisible by remember { mutableStateOf(true) }
    // 建立PreviewView
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    // 设置cameraController
    previewView.controller = cameraController
    // 初始化CameraProvider的监听器
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    // 初始化imageCapture用例
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // 初始化CameraProvider
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(cameraProviderFuture) {
        // 监听CameraProvider的变化，从监听器中获取CameraProvider实例
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        val provider = cameraProvider ?: return@DisposableEffect onDispose {}
        // 设定后置镜头
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        // 设定长宽比
        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_16_9,
            AspectRatioStrategy.FALLBACK_RULE_AUTO
            )

        // 设定preview分辨率
        val previewResolutionStrategy = ResolutionStrategy(
            Size(1080, 1920),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
        )
        // 设定preview分辨率选择器
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .setResolutionStrategy(previewResolutionStrategy)
            .build()
        // 设定预览UseCase
        val preview = Preview.Builder().setResolutionSelector(resolutionSelector).build()
           .also { it.surfaceProvider = previewView.surfaceProvider }

        // 设定照片分辨率
        val imageCaptureResolutionStrategy = ResolutionStrategy(
            Size(1080, 1920),
            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
        )
        // 设定照片分辨率选择器
        val imageCaptureResolutionSelector = ResolutionSelector.Builder()
           .setAspectRatioStrategy(aspectRatioStrategy)
           .setResolutionStrategy(imageCaptureResolutionStrategy)
           .build()
        // 创建imageCapture用例(UseCase)
        imageCapture = ImageCapture.Builder().setResolutionSelector(imageCaptureResolutionSelector).build()
        val useCaseGroup =
            UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageCapture!!).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, cameraSelector, useCaseGroup)
        } catch (e: Exception) {
            Log.e("ManualCameraScreen", "Failed to bind cameras", e)
            e.printStackTrace()
        }
        onDispose {
            provider.unbindAll()
        }
    }
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (previewRef, buttonRef, sliderRef) = createRefs()
        val scope = rememberCoroutineScope()
        // 定义预览view
        AnimatedVisibility(
            visible = stateOfViewIsVisible,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.constrainAs(previewRef) {
                top.linkTo(parent.top, 20.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
            AndroidView(factory = { previewView })
        }
        // 定义调焦slider
        Column(
            modifier = Modifier.constrainAs(sliderRef) {
                top.linkTo(previewRef.bottom, 30.dp)
                start.linkTo(previewRef.start)
                end.linkTo(previewRef.end)
            }
        ) {
            Text(text = "Focus Distance: $stateOfFocusDistance")
            Slider(
                modifier = Modifier
                    .border(2.dp, MaterialTheme.colorScheme.primary),
                value = stateOfFocusDistance,
                onValueChange = { stateOfFocusDistance = it },
                valueRange = 0f..minFocusDistance
            )
        }
        // 定义拍照按钮
        IconButton(modifier = Modifier.constrainAs(buttonRef) {
            top.linkTo(previewRef.bottom, 30.dp)
            start.linkTo(previewRef.start)
            end.linkTo(previewRef.end)
        }, onClick = {
            // 点击拍照按钮时，将前摄像头的预览设置为不可见
            stateOfViewIsVisible = false
            // 拍照
            takePicture(context, imageCapture!!, shutterSound)
            scope.launch {
                delay(300)
                // 拍照完成后，将预览设置为可见
                stateOfViewIsVisible = true
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

