package com.example.deep_camera

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
import androidx.concurrent.futures.await
import androidx.core.content.edit
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object Util {

    /**
     * 拍照
     * @param context 上下文
     * @param imageCapture 图片捕获器
     * @param executor 执行器
     * @param shutterSound 快门音效
     */
    private fun takePicture(
        context: Context,
        imageCapture: ImageCapture,
        executor: java.util.concurrent.Executor,
        shutterSound: ShutterSound? = null
    ) {
        // 取得OutputFileOptions
        val outputFileOptions = getOutputFileOptions(context)
        try {
            shutterSound?.play()
            imageCapture.takePicture(
                outputFileOptions, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Log.i("takePictures", "Image saved to ${outputFileResults.savedUri}")
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("takePictures", "Image capture failed", exception)
                        exception.printStackTrace()
                    }
                })
        } catch (exc: Exception) {
            Log.e("takePictures", "Image capture failed", exc)
        }
    }

    /**
     * 拍照
     * @param context 上下文
     * @param imageCapture 图片捕获器
     * @param camera 相机
     * @param shutterSound 快门音效
     * @param focusArray 对焦距离数组
     */
    fun takePictures(
        context: Context,
        imageCapture: ImageCapture,
        camera: Camera,
        shutterSound: ShutterSound? = null,
        focusArray: Array<FocusItem>
    ) {
        val cameraControl = camera.cameraControl
        val executorService = Executors.newSingleThreadExecutor()
        // 协程作用域内使用 for 循环顺序处理所有对焦项
        CoroutineScope(Dispatchers.Main).launch {
            for (focusItem in focusArray) {
                if (focusItem.selected) {
                    try {
                        //  设置焦距，等待完成
                        setFocusDistance(cameraControl, focusItem.focusAt).await()
                        Log.i(
                            "takePictures",
                            "setLinearZoom completed for focus: ${focusItem.focusAt}"
                        )
                        // 拍照
                        takePicture(
                            context = context,
                            imageCapture = imageCapture,
                            executor = executorService,
                            shutterSound = shutterSound
                        )
                        // 等待1秒确保拍照完成
                        delay(1000)
                    } catch (e: Exception) {
                        Log.e("takePictures", "operation failed", e)
                        e.printStackTrace()
                    }
                }
            }
            executorService.shutdown()
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

    /**
     * 获取Preview
     * @return Preview
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun getPreview(): Preview {
        // 定义ResolutionStrategy
        val resolutionStrategy = ResolutionStrategy(Size(1920, 1080), FALLBACK_RULE_CLOSEST_LOWER)
        // 定义AspectRatioStrategy
        val aspectRatioStrategy =
            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        // 定义ResolutionSelector
        val resolutionSelector =
            ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatioStrategy)
                .setResolutionStrategy(resolutionStrategy).build()
        return Preview.Builder().setResolutionSelector(resolutionSelector).build()
    }

    /**
     * 获取ImageCapture
     * @return ImageCapture
     */
    fun getImageCapture(): ImageCapture {
        // 定义ResolutionStrategy
        val resolutionStrategy = ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY
        // 定义AspectRatioStrategy
        val aspectRatioStrategy =
            AspectRatioStrategy(AspectRatio.RATIO_16_9, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        // 定义ResolutionSelector
        val resolutionSelector =
            ResolutionSelector.Builder().setAspectRatioStrategy(aspectRatioStrategy)
                .setResolutionStrategy(resolutionStrategy).build()
        return ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(resolutionSelector).build()
    }

    /**
     * 获取ImageCapture的输出文件选项
     * @param context 上下文
     * @return ImageCapture.OutputFileOptions
     */
    private fun getOutputFileOptions(context: Context): ImageCapture.OutputFileOptions {
        val contentValues = ContentValues().apply {
            val simpleDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            val currentDateTime = simpleDateFormat.format(Date())
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${currentDateTime}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()
    }

    /**
     * 设置缩放比例
     * @param cameraControl 相机控制
     * @param zoomRatio 缩放比例
     * @return 监听Future
     */
    fun setZoomRatio(
        cameraControl: CameraControl, zoomRatio: Float?
    ): ListenableFuture<Void?> {
        val clampedZoomRatio = zoomRatio?.coerceIn(0f, 1f) ?: 0f
        return cameraControl.setLinearZoom(clampedZoomRatio)
    }

    /**
     * 设置焦距
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun setFocusDistance(
        cameraControl: CameraControl, focusDistance: Float
    ): ListenableFuture<Void?> {
        val camera2CameraControl = Camera2CameraControl.from(cameraControl)
        val captureRequestOptions = CaptureRequestOptions.Builder().setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF
        ).setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance).build()
        return camera2CameraControl.setCaptureRequestOptions(captureRequestOptions)
    }

    /**
     * 获取相机的对焦范围
     * @param context 上下文
     * @return 对焦范围
     */
    fun getFocusDistanceInfo(context: Context): FocusDistanceInfo {
        val cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull {
            val characteristics = cameraManager.getCameraCharacteristics(it)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK
        }
        if (cameraId == null) {
            Log.e("getMinFocusDistance", "No back camera found")
            return FocusDistanceInfo(0f, 0f, "no-camera-found")
        }
        val characteristic = cameraManager.getCameraCharacteristics(cameraId)
        // 检查是否支持手动对焦
        val afAvailability = characteristic.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afAvailability == null || !afAvailability.contains(CameraMetadata.CONTROL_AF_MODE_OFF)) {
            Log.e("getMinFocusDistance", "Camera does not support manual focus")
            return FocusDistanceInfo(0f, 0f, "non-focus-support")
        }
        // 获取镜头是否校准
        val focusDistanceCalibration =
            when (characteristic.get(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION)) {
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_UNCALIBRATED -> "uncalibrated"
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_CALIBRATED -> "calibrated"
                CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION_APPROXIMATE -> "approximate"
                else -> "unknown"
            }
        Log.i("getMinFocusDistance", "isLensCalibrated: $focusDistanceCalibration")
        // 获取焦点范围
        val minFocusDistance = characteristic.get(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "minFocusDistance: $minFocusDistance")
        // 获取超焦距
        val hyperFocalDistance = characteristic.get(
            CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "hyperFocalDistance: $hyperFocalDistance")
        return FocusDistanceInfo(minFocusDistance, hyperFocalDistance, focusDistanceCalibration)
    }

    /**
     * 初始化对焦距离数组
     * @param minFocusDistance 最小对焦距离
     * @return 对焦距离数组
     */
    fun initFocusArray(minFocusDistance: Float, hyperFocalDistance: Float): Array<FocusItem> {
        if (minFocusDistance == 0f) {
            return arrayOf(FocusItem(0.0F, true))
        } else {
            // 从hyperFocalDistance开始直到minFocusDistance,等分为6个焦点距离
            // 倒数第二个焦点距离为超焦距
            // 最后一个焦点距离为0f（无穷远）
            val step = (minFocusDistance - hyperFocalDistance) / 6
            return arrayOf(
                FocusItem(minFocusDistance, true),
                FocusItem(step * 5, true),
                FocusItem(step * 4, true),
                FocusItem(step * 3, true),
                FocusItem(step * 2, true),
                FocusItem(step * 1, true),
                FocusItem(hyperFocalDistance, true),
                FocusItem(0f, true)
            )
        }
    }

}
