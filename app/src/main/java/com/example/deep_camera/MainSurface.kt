package com.example.deep_camera

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSurface(
    navController: NavController? = null,
    sharedPreferences: SharedPreferences? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusList = loadFocusArray(sharedPreferences) ?: defaultFocusArray
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Deep Camera Main") },
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                        onClick = {
                            navController?.navigate("settings") {
                                popUpTo("main") {
                                    inclusive = true
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                })
        },
        bottomBar = {
            BottomAppBar {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary),
                    onClick = { takePictures(context, lifecycleOwner, focusList) }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.circle),
                        contentDescription = "Shutter"
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    ) { paddingValues ->
        Modifier.padding(paddingValues).Camera()
    }
}

@Preview(showBackground = true)
@Composable
fun MainSurfacePreview() {
    MainSurface()
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun takePictures(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    focusList: Array<FocusItem>
) {
    // 获取 CameraCharacteristics
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE)
            as android.hardware.camera2.CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull {
        val characteristics = cameraManager.getCameraCharacteristics(it)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
    }
    if (cameraId == null) {
        Log.e("MainSurface", "No back camera found")
        return
    }
    val characteristic = cameraManager.getCameraCharacteristics(cameraId)
    // 检查是否支持手动对焦
    val afAvailability = characteristic.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
    if (afAvailability == null || !afAvailability.contains(CameraMetadata.CONTROL_AF_MODE_OFF)) {
        Log.e("MainSurface", "Camera does not support manual focus")
        return
    }
    // *************************************************************************************
    // 获取焦点范围
    val minFocusDistance = characteristic.get(
        CameraCharacteristics
            .LENS_INFO_MINIMUM_FOCUS_DISTANCE
    ) ?: 0f
    Log.i("MainSurface", "minFocusDistance: $minFocusDistance")
    val hyperFocalDistance = characteristic.get(
        CameraCharacteristics
            .LENS_INFO_HYPERFOCAL_DISTANCE
    ) ?: 0f
    Log.i("MainSurface", "hyperFocalDistance: $hyperFocalDistance")
    // *************************************************************************************

    // 获取 CameraProvider
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    // 创建一个 CameraSelector
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()
    // 定义 ImageCapture 对象
    val imageCapture = ImageCapture.Builder().build()
    var camera: Camera? = null
    try {
        // 解绑之前绑定的用例
        cameraProvider.unbindAll()
        // 将 ImageCapture 用例绑定到相机
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            imageCapture
        )
    } catch (exc: Exception) {
        Log.e("MainSurface", "Use case binding failed", exc)
    }
    // 获取 cameraControl
    val cameraControl = camera?.cameraControl
    if (cameraControl == null) {
        Log.e("MainSurface", "Camera control is null")
        return
    }
    val camera2CameraControl = Camera2CameraControl.from(cameraControl)
    val outputDirectory = context.filesDir
    // 根据focusList中的selected属性设置对焦距离，并拍照。
    focusList.forEach { focusItem ->
        if (focusItem.selected) {
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusItem.focusAt)
                .build()
            val future: ListenableFuture<Void> =
                camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
            future.addListener({
                if (future.isDone && !future.isCancelled) {
                    // setCaptureRequestOptions操作成功完成
                    Log.i("MainSurface", "Focus distance set to ${focusItem.focusAt}")
                    try {
                        // 创建一个临时文件来保存图片
                        val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
                        val outputOptions =
                            ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        // 执行拍照操作
                        imageCapture.takePicture(
                            outputOptions, Executors.newSingleThreadExecutor(),
                            object : OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    Log.i("MainSurface", "Image saved to ${photoFile.absolutePath}")
                                }
                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("MainSurface", "Image capture failed", exception)
                                }
                            })
                    } catch (exc: Exception) {
                        Log.e("MainSurface", "Image capture failed", exc)
                    }
                }
            }, Executors.newSingleThreadExecutor())
        }
    }
}