package com.example.manual_camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
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

    fun takePicture(
        context: Context,
        cameraController: CameraController,
        shutterSound: ShutterSound? = null,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        shutterSound?.play()
        cameraController.takePicture(outputOptions, executor,
            object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.i("takePictures", "Image saved to ${outputFileResults.savedUri}")
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("takePictures", "Image capture failed", exception)
            }
        })
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun changeFocusDistance(cameraControl: CameraControl, distance: Float) {
        val camera2CameraControl = Camera2CameraControl.from(cameraControl)
        // 关闭自动对焦、设置焦距
        val captureRequestOptions = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
            .build()
        camera2CameraControl.captureRequestOptions = captureRequestOptions
    }
}
