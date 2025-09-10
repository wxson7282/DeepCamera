package com.example.deep_camera

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
import androidx.concurrent.futures.await
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                        // 等待动作完成
                        cameraControl.setLinearZoom(focusItem.focusAt).await()
                        Log.i("takePictures", "setLinearZoom completed for focus: ${focusItem.focusAt}")
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
        // 定义Preview
        return Preview.Builder().setResolutionSelector(resolutionSelector).build()
    }

    /**
     * 获取ImageCapture
     * @return ImageCapture
     */
    fun getImageCapture(): ImageCapture {
        // 定义ResolutionStrategy
        val resolutionStrategy = ResolutionStrategy(Size(1920, 1080), FALLBACK_RULE_CLOSEST_LOWER)
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
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        ).build()
    }
}
