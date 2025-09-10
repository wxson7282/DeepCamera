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
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
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

    fun takePictures(
        context: Context,
        imageCapture: ImageCapture,
        camera: Camera,
        shutterSound: ShutterSound? = null,
        focusArray: Array<FocusItem>
    ) {
        val cameraControl = camera.cameraControl
        val executorService = Executors.newSingleThreadExecutor()
        // 使用协程和递归实现顺序执行
        CoroutineScope(Dispatchers.Main).launch {
            processFocusItemsSequentially(
                context, imageCapture, cameraControl, executorService,
                shutterSound, focusArray, 0
            )        }
    }

    private suspend fun processFocusItemsSequentially(
        context: Context,
        imageCapture: ImageCapture,
        cameraControl: CameraControl,
        executorService: ExecutorService,
        shutterSound: ShutterSound?,
        focusArray: Array<FocusItem>,
        index: Int
    ) {
        if (index >= focusArray.size) {
            // 所有对焦项处理完成
            executorService.shutdown() // 关闭执行器
            return
        }
        val focusItem = focusArray[index]
        if (focusItem.selected) {
            val listenableFuture = cameraControl.setLinearZoom(focusItem.focusAt)
            // 等待异步操作完成
            try {
                // 等待异步操作完成
                listenableFuture.await()
            } catch (e: Exception) {
                Log.e("takePictures", "setLinearZoom operation failed", e)
                e.printStackTrace()
            }

            try{
                takePicture(
                    context = context,
                    imageCapture = imageCapture,
                    executor = executorService,
                    shutterSound = shutterSound
                )

                // 等待1秒确保拍照完成
                delay(1000)

            }catch (e: Exception) {
                Log.e("takePictures", "takePicture operation failed", e)
                e.printStackTrace()
            }
        }
        // 递归处理下一个对焦项
        processFocusItemsSequentially(
            context, imageCapture, cameraControl, executorService,
            shutterSound, focusArray, index + 1
        )
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

//    @OptIn(ExperimentalCamera2Interop::class)
//    private fun getCaptureRequestOptions(focusAt: Float): CaptureRequestOptions {
//        return CaptureRequestOptions.Builder().setCaptureRequestOption(
//                CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF
//            ).setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focusAt).build()
//    }
}
