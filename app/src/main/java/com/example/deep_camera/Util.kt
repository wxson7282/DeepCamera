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
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors
import kotlin.collections.contains

object Util {

    /**
     * 拍照并保存到指定文件夹
     * @param context 上下文
     * @param lifecycleOwner LifecycleOwner
     * @param focusList 对焦距离列表
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    fun takePictures(
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
            Log.e("takePictures", "Use case binding failed", exc)
        }
        // 获取 cameraControl
        val cameraControl = camera?.cameraControl
        if (cameraControl == null) {
            Log.e("takePictures", "Camera control is null")
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
                        Log.i("takePictures", "Focus distance set to ${focusItem.focusAt}")
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
                                        Log.i("takePictures", "Image saved to ${photoFile.absolutePath}")
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("takePictures", "Image capture failed", exception)
                                    }
                                })
                        } catch (exc: Exception) {
                            Log.e("takePictures", "Image capture failed", exc)
                        }
                    }
                }, Executors.newSingleThreadExecutor())
            }
        }
    }

    /**
     * 获取相机的最小对焦距离
     * @param context 上下文
     * @return 最小对焦距离
     */
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
        // 获取焦点范围
        val minFocusDistance = characteristic.get(
            CameraCharacteristics
                .LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        Log.i("getMinFocusDistance", "minFocusDistance: $minFocusDistance")
        return minFocusDistance
    }

    /**
     * 初始化对焦距离数组
     * @param minFocusDistance 最小对焦距离
     * @return 对焦距离数组
     */
    fun initFocusArray(minFocusDistance: Float) : Array<FocusItem>  {
        if (minFocusDistance == 0f) {
            return arrayOf<FocusItem>(FocusItem(0.0F, true))
        } else {
            // 从0.0f开始直到minFocusDistance,等分为7个焦点距离
            val step = minFocusDistance / 7
            return arrayOf<FocusItem>(
                FocusItem(0.0F, true),
                FocusItem(step, true),
                FocusItem(step * 2, true),
                FocusItem(step * 3, true),
                FocusItem(step * 4, true),
                FocusItem(step * 5, true),
                FocusItem(step * 6, true),
                FocusItem(minFocusDistance, true)
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
