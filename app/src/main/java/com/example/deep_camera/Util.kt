package com.example.deep_camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
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
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors

object Util {

    /**
     * 拍照
     * @param context 上下文
     * @param imageCapture 图片捕获器
     * @param executor 执行器
     * @param setStateOfImageSaved 拍照完成后回调函数，用于通知上层界面拍照完成
     */
    fun takePicture(
        context: Context,
        imageCapture: ImageCapture,
        executor: java.util.concurrent.Executor,
        shutterSound: ShutterSound? = null,
        setStateOfImageSaved: (Boolean) -> Unit = {}
    ) {
        try {
            // 创建一个临时文件来保存图片
            val photoFile = File(context.filesDir, "${System.currentTimeMillis()}.jpg")
            val outputOptions =
                ImageCapture.OutputFileOptions.Builder(photoFile).build()
            shutterSound?.play()
            // 执行拍照操作
            imageCapture.takePicture(
                outputOptions, executor,
                object : OnImageSavedCallback {
                    @SuppressLint("RestrictedApi", "VisibleForTests")
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.i("takePictures", "Image saved to ${photoFile.absolutePath}")
                        setStateOfImageSaved(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("takePictures", "Image capture failed", exception)
                    }
                })
        } catch (exc: Exception) {
            Log.e("takePictures", "Image capture failed", exc)
        }
    }

    /**
     * 拍照
     * @param context 上下文
     * @param lifecycleOwner 生命周期所有者
     * @param focusArray 对焦距离数组
     * @param setStateOfLastImageSaved 拍照完成后回调函数，用于通知上层界面拍照完成
     */
    @SuppressLint("RestrictedApi", "VisibleForTests")
    @OptIn(ExperimentalCamera2Interop::class)
    fun takeAllPictures(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        focusArray: Array<FocusItem>,
        shutterSound: ShutterSound? = null,
        setStateOfLastImageSaved: (Boolean) -> Unit = {}
    ) {
        val executor = Executors.newSingleThreadExecutor()
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
            Log.e("takePictures", "Use case binding failed", exc)
            return
        }
        val cameraControl = camera.cameraControl
        val camera2CameraControl = Camera2CameraControl.from(cameraControl)
        // 从focusArray中取出selected为true的项，组成一个新数组
        val selectedFocusArray = focusArray.filter { it.selected }
        selectedFocusArray.forEach { focusItem ->
            val captureRequestOptions = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CameraMetadata.CONTROL_AF_MODE_OFF
                )
                .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusItem.focusAt)
                .build()
            camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
            Thread.sleep(1000)
            Log.i("takePictures", "Focus distance set to ${focusItem.focusAt}")
            // 最后一张照片取得后，通知上层界面拍照完成
            if (focusItem == selectedFocusArray.last()) {
                Log.i("takePictures", "Take last picture")
                takePicture(
                    context = context,
                    imageCapture = imageCapture,
                    executor = executor,
                    shutterSound = shutterSound,
                    setStateOfImageSaved = setStateOfLastImageSaved
                )
            } else {
                Log.i("takePictures", "Take picture")
                takePicture(
                    context = context,
                    imageCapture = imageCapture,
                    shutterSound = shutterSound,
                    executor = executor
                )
            }
        }
    }

    /**
     * 获取相机的对焦范围
     * @param context 上下文
     * @return 对焦范围
     */
    fun getFocusDistance(context: Context): FocusDistance {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE)
                as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        }
        if (cameraId == null) {
            Log.e("getMinFocusDistance", "No back camera found")
            return FocusDistance(0f, 0f)
        }
        val characteristic = cameraManager.getCameraCharacteristics(cameraId)
        // 检查是否支持手动对焦
        val afAvailability = characteristic.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afAvailability == null || !afAvailability.contains(CameraMetadata.CONTROL_AF_MODE_OFF)) {
            Log.e("getMinFocusDistance", "Camera does not support manual focus")
            return FocusDistance(0f, 0f)
        }
        // 获取焦点范围
        val minFocusDistance = characteristic.get(
            CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "minFocusDistance: $minFocusDistance")
        // 获取超焦距
        val hyperFocalDistance = characteristic.get(
            CameraCharacteristics
                .LENS_INFO_HYPERFOCAL_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "hyperFocalDistance: $hyperFocalDistance")
        return FocusDistance(minFocusDistance, hyperFocalDistance)
    }

    /**
     * 初始化对焦距离数组
     * @param minFocusDistance 最小对焦距离
     * @return 对焦距离数组
     */
    fun initFocusArray(minFocusDistance: Float, hyperFocalDistance: Float): Array<FocusItem> {
        if (minFocusDistance == 0f) {
            return arrayOf<FocusItem>(FocusItem(0.0F, true))
        } else {
            // 从0.0f开始直到minFocusDistance,等分为7个焦点距离
            val step = (minFocusDistance - hyperFocalDistance) / 7
            return arrayOf<FocusItem>(
                FocusItem(minFocusDistance, true),
                FocusItem(step * 6, true),
                FocusItem(step * 5, true),
                FocusItem(step * 4, true),
                FocusItem(step * 3, true),
                FocusItem(step * 2, true),
                FocusItem(step, true),
                FocusItem(hyperFocalDistance, true)
            )
        }
    }

    /**
     * 从SharedPreferences中加载对焦距离数组
     * @param sharedPreferences SharedPreferences
     * @return 对焦距离数组
     */
    fun loadFocusArray(sharedPreferences: SharedPreferences?): Array<FocusItem>? {
        val json = sharedPreferences?.getString("focusArray", null)
        return json?.let { Gson().fromJson(it, Array<FocusItem>::class.java) }
    }

    /**
     * 保存对焦距离数组到SharedPreferences
     * @param sharedPreferences SharedPreferences
     * @param focusArray 对焦距离数组
     */
    fun saveFocusArray(sharedPreferences: SharedPreferences, focusArray: Array<FocusItem>) {
        val json = Gson().toJson(focusArray)
        sharedPreferences.edit { putString("focusArray", json) }
    }

}
