package com.example.deep_camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.collections.contains

@OptIn(ExperimentalCamera2Interop::class)
fun takePictures1(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    focusList: Array<FocusItem>
) {
    // 获取 CameraProvider
    val cameraProvider = ProcessCameraProvider.getInstance(context).get()
    // 创建一个 CameraSelector
    val cameraSelector = CameraSelector.Builder()
        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
        .build()

    val imageCaptureBuilder = ImageCapture.Builder()
    val camera2Interop = Camera2Interop.Extender<ImageCapture>(imageCaptureBuilder)
    // 定义 ImageCapture 对象
    val imageCapture = imageCaptureBuilder.build()
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
        Log.e("takePictures", "Use case binding failed", exc)
    }
    // 获取 cameraControl
    val cameraControl = camera?.cameraControl
    if (cameraControl == null) {
        Log.e("takePictures", "Camera control is null")
        return
    }
    val camera2CameraControl = Camera2CameraControl.from(cameraControl)

    // 检查相机是否支持手动对焦
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull {
        val characteristics = cameraManager.getCameraCharacteristics(it)
        characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
    }
    if (cameraId == null) {
        Log.e("takePictures", "No back camera found")
        return
    }
    val characteristic = cameraManager.getCameraCharacteristics(cameraId)
    val deviceLevel = characteristic.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
    Log.i("takePictures", "deviceLevel: $deviceLevel")
    val afAvailability = characteristic.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
    if (afAvailability == null || !afAvailability.contains(CameraMetadata.CONTROL_AF_MODE_OFF)) {
        Log.e("takePictures", "Camera does not support manual focus")
        return
    }

    val singleThreadExecutor = Executors.newSingleThreadExecutor()
    // 根据focusList中的selected属性设置对焦距离，并拍照。
    focusList.forEach { focusItem ->
        if (focusItem.selected) {
            val focusDistance = focusItem.focusAt
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
                .build()
            // 添加回调来监听对焦状态
            val afStateCallback = object : CameraCaptureSession.CaptureCallback() {

                private var lastFocusDistance: Float? = null
                private var isFocusLocked = false

                override fun onCaptureStarted(session: CameraCaptureSession,
                                     request: CaptureRequest,
                                     timestamp: Long,
                                     frameNumber: Long) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber)
                    Log.i("takePictures", "onCaptureStarted")
                }
                override fun onCaptureProgressed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    partialResult: CaptureResult
                ) {
                    super.onCaptureProgressed(session, request, partialResult)
                    Log.i("takePictures", "onCaptureProgressed")
                    handleFocusState(partialResult)
                }

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Log.i("takePictures", "onCaptureCompleted")
                    handleFocusState(result)
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    super.onCaptureFailed(session, request, failure)
                    Log.e("takePictures", "onCaptureFailed")
                }

                private fun handleFocusState(result: CaptureResult) {
                    // 获取当前对焦状态
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    // 获取当前实际的对焦距离
                    val currentFocusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                    // 检查对焦距离是否已达到目标值
                    if (currentFocusDistance != null) {
                        if (lastFocusDistance == null || lastFocusDistance != currentFocusDistance) {
                            Log.i("takePictures", "对焦距离更新: 当前=$currentFocusDistance, 目标=$focusDistance")
                            lastFocusDistance = currentFocusDistance
                        }
                        // 判断对焦是否已完成（根据实际设备精度调整容差）
                        val tolerance = 0.01f
                        if (Math.abs(currentFocusDistance - focusDistance) < tolerance) {
                            if (!isFocusLocked) {
                                Log.i("takePictures", "焦距已锁定在目标值: $focusDistance")
                                isFocusLocked = true
                                // 在这里执行焦距锁定后的操作: 拍照
                                Util.takePicture(context, imageCapture, singleThreadExecutor)
                            }
                        }
                    }
                    // 同时检查AF状态（如果使用AF模式）
                    if (afState != null) {
                        when (afState) {
                            CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> {
                                Log.d("takePictures", "AF状态: 对焦已锁定, AF状态=$afState")
                            }
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED,
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> {
                                Log.d("takePictures", "AF状态: 被动对焦完成, AF状态=$afState")
                            }
                            CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN,
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> {
                                Log.d("takePictures", "AF状态: 正在对焦扫描, AF状态=$afState")
                            }
                            else -> {
                                Log.d("takePictures", "AF状态: $afState")
                            }
                        }
                    }
                }
            }
            try {
                camera2Interop.setSessionCaptureCallback(afStateCallback)
                camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
            } catch (e: Exception) {
                Log.e("takePictures", "设置对焦距离失败", e)
            }
        }
    }
}