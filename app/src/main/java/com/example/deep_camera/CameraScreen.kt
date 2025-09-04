package com.example.deep_camera

import android.content.SharedPreferences
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
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

@Composable
fun CameraScreen(
    sharedPreferences: SharedPreferences? = null,
    shutterSound: ShutterSound? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusArray = Util.loadFocusArray(sharedPreferences) ?: defaultFocusArray
    // 初始化CameraProvider的监听器
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    // 初始化CameraProvider
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    // 初始化摄像头的PreviewView
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // 创建拍照用例
    val imageCapture = remember { Util.getImageCapture() }
    // 创建预览用例
    val preview = remember { Util.getPreview() }
    // 使用previewView的SurfaceProvider来配置预览用例
    preview.setSurfaceProvider(previewView.surfaceProvider)

    LaunchedEffect(cameraProviderFuture) {
        // 监听CameraProvider的变化，从监听器中获取CameraProvider实例
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(lifecycleOwner, cameraProvider) {
        // 如果CameraProvider为空，则不执行任何操作
        val cameraProvider = cameraProvider ?: return@DisposableEffect onDispose {}
        //定义后摄像头的选择器
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            // 解除既有绑定
            cameraProvider.unbindAll()
            // 绑定新的用例
            camera = cameraProvider.bindToLifecycle(lifecycleOwner,cameraSelector, imageCapture, preview)
        } catch (e: IllegalStateException) {
            Log.e("CameraScreen", "the use case has already been bound to another lifecycle or method is not called on main thread", e)
        } catch (e: IllegalArgumentException) {
            Log.e("CameraScreen", "the provided camera selector is unable to resolve a camera to be used for the given use cases", e)
        } catch (e: Exception) {
            Log.e("CameraScreen", "Error binding camera use cases", e)
            e.printStackTrace()
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val (previewRef, buttonRef) = createRefs()
        // 定义预览view
        AndroidView(factory = { previewView }, modifier = Modifier.constrainAs(previewRef) {
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        })
        // 定义拍照按钮
        IconButton(modifier = Modifier.constrainAs(buttonRef) {
            bottom.linkTo(parent.bottom, 15.dp)
            start.linkTo(parent.start)
            end.linkTo(parent.end)
        }, onClick = {
            // 拍照
            Util.takePictures(
                context = context,
                imageCapture = imageCapture,
                camera = camera!!,
                shutterSound = shutterSound,
                focusArray = focusArray
            )
        }) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.circle),
                tint = Color.Unspecified,
                contentDescription = "Shutter"
            )
        }
    }
}
