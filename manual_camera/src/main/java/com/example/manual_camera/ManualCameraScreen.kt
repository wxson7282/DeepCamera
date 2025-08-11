package com.example.manual_camera

import android.util.Log
import android.util.Size
import android.widget.LinearLayout
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
    // 定义cameraController以实现cameraX的大多数功能
    val cameraController = LifecycleCameraController(context)
    // 设定后置镜头
    cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    // 获取后置相机的最近焦距（最大值）
    val minFocusDistance = Util.getMinFocusDistance(context)
    // 焦距变量
    var stateOfFocusDistance by remember { mutableFloatStateOf(0.5f) }
    // 定义previewView是否可见
    var stateOfViewIsVisible by remember { mutableStateOf(true) }
    // 建立PreviewView
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(720, 1280)
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            setBackgroundColor(Color.Gray.toArgb())
        }
    }

    // 预处理和收尾处理
    DisposableEffect(lifecycleOwner, cameraController) {
        // 设定照片分辨率
        cameraController.imageCaptureTargetSize = CameraController.OutputSize(Size(2160, 3840))
        try {
            // 解绑
            cameraController.unbind()
            // 绑定lifecycleOwner
            cameraController.bindToLifecycle(lifecycleOwner)
            // 为previewView设置cameraController
            previewView.controller = cameraController
        } catch (e: Exception) {
            Log.e("ManualCameraScreen", "Failed to bind cameras", e)
            e.printStackTrace()
        }
        onDispose {
            cameraController.unbind()
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
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
            AndroidView(factory = { previewView })
        }
        // 定义调焦slider
        Column(
            modifier = Modifier.constrainAs(sliderRef) {
                top.linkTo(parent.top, 20.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }) {
            Text(text = "Focus Distance: $stateOfFocusDistance")
            Slider(
                modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary),
                value = stateOfFocusDistance,
                onValueChange = { stateOfFocusDistance = it },
                valueRange = 0f..minFocusDistance,
                onValueChangeFinished = {
                    // 当slider值改变完成时，将slider的值设置为cameraController的focusDistance
                    cameraController.cameraControl?.let {
                        Util.changeFocusDistance(it, stateOfFocusDistance)
                    }
                })
        }
        // 定义拍照按钮
        IconButton(modifier = Modifier.constrainAs(buttonRef) {
            bottom.linkTo(parent.bottom, 15.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }, onClick = {
            // 点击拍照按钮时，将前摄像头的预览设置为不可见
            stateOfViewIsVisible = false
            // 拍照
            takePicture(context, cameraController, shutterSound)

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

