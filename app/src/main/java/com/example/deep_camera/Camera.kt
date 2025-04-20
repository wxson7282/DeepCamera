package com.example.deep_camera

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.lifecycle.ExperimentalCameraProviderConfiguration
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.ProcessCameraProvider.configureInstance
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking

@Composable
fun Camera(modifier: Modifier = Modifier) {
    // 在这里编写您的屏幕内容
    Log.i("Camera", "Start")
    Column(
        modifier = modifier
    ) {
        CameraPreview()
        Spacer(modifier = Modifier.height(10.dp))
        CameraPanel()
    }
}

@OptIn(ExperimentalCameraProviderConfiguration::class)
@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    // 在这里编写您的预览内容
    val context = LocalContext.current
    val cameraController = LifecycleCameraController(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = Modifier.height(460.dp),
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FIT_CENTER    // 视频帧位于中心，无拉伸变形
                implementationMode = PreviewView.ImplementationMode.PERFORMANCE  // SurfaceView 模式
                setBackgroundColor(Color.Black.toArgb())
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

@Composable
private fun CameraPanel(modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
            .border(
                1.dp, MaterialTheme.colorScheme.primary
            ),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 列举全部镜头
//        val context = LocalContext.current
//        val cameraLensList = getCameraLensList(context)
        val cameraLensList = getCameraList()
        val checkedStates = remember {
            mutableStateListOf<Boolean>().apply {
                repeat(cameraLensList.size) { add(false) }
            }
        }
        cameraLensList.forEachIndexed { index, cameraSelector ->
            Column {
                Text(text = "镜头$index")
                Checkbox(
                    checked = checkedStates[index],
                    onCheckedChange = { checkedStates[index] = it }
                )
            }
        }
    }
}

private fun getCameraLensList(context: Context): List<CameraSelector> {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val cameraSelectors = mutableListOf<CameraSelector>()

    try {
        val cameraProvider = cameraProviderFuture.get()
        // 获取所有符合条件的相机Info
        val cameraInfoList = cameraProvider.availableCameraInfos.filter {
            it.lensFacing == CameraSelector.LENS_FACING_BACK
        }
        // 为每个后置相机创建一个 CameraSelector
        cameraInfoList.forEachIndexed { index, cameraInfo ->
            val cameraSelector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { it == cameraInfo }
                }
                .build()
            cameraSelectors.add(cameraSelector)
        }
    } catch (e: Exception) {
        Log.e("CameraUtils", "Failed to get camera provider", e)
    }

    return cameraSelectors
}


/**
 * 获取相机列表 only for test
 */
private fun getCameraList(): List<CameraSelector> {
    return mutableListOf<CameraSelector>(
        CameraSelector.DEFAULT_BACK_CAMERA,
        CameraSelector.DEFAULT_FRONT_CAMERA
    )
}
