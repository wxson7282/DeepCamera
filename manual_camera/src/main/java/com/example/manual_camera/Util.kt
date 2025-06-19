package com.example.manual_camera

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
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

object Util {
    fun getMinFocusDistance(context: Context): Float {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE)
                as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        }
        if (cameraId == null) {
            Log.e("getMinFocusDistance", "No back camera found")
            return 0f
        }
        val characteristic = cameraManager.getCameraCharacteristics(cameraId)
        // 检查是否支持手动对焦
        val afAvailability = characteristic.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afAvailability == null || !afAvailability.contains(CameraMetadata.CONTROL_AF_MODE_OFF)) {
            Log.e("getMinFocusDistance", "Camera does not support manual focus")
            return 0f
        }
        // 获取最近焦距（最大值）
        val minFocusDistance = characteristic.get(
            CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "minFocusDistance: $minFocusDistance")
        return minFocusDistance
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun getCamera2CameraControl(
        context: Context,
        lifecycleOwner: LifecycleOwner
    ) : Camera2CameraControl? {
        val imageCaptureBuilder = ImageCapture.Builder()
        val camera2Interop = Camera2Interop.Extender<ImageCapture>(imageCaptureBuilder)
        val cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureStarted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                timestamp: Long,
                frameNumber: Long
            ) {
                super.onCaptureStarted(session, request, timestamp, frameNumber)
            }

            override fun onCaptureProgressed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                partialResult: CaptureResult
            ) {
                super.onCaptureProgressed(session, request, partialResult)
            }

            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                super.onCaptureCompleted(session, request, result)
            }

            override fun onCaptureFailed(
                session: CameraCaptureSession,
                request: CaptureRequest,
                failure: CaptureFailure
            ) {
                super.onCaptureFailed(session, request, failure)
                Log.e("takePictures", "onCaptureFailed")
            }
        }
        camera2Interop.setSessionCaptureCallback(captureCallback)
        var camera: Camera? = null
        lateinit var imageCapture: ImageCapture
        try {
            cameraProvider.unbindAll()
            imageCapture = imageCaptureBuilder.build()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageCapture
            )
        } catch (exc: Exception) {
            Log.e("getCamera2CameraControl", "Use case binding failed", exc)
            return null
        }
        val cameraControl = camera.cameraControl
        return Camera2CameraControl.from(cameraControl)
    }
}

