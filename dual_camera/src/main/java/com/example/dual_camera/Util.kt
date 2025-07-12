package com.example.dual_camera

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.util.concurrent.Executors

object Util {
    fun takePicture(
        context: Context,
        imageCapture: ImageCapture,
        shutterSound: ShutterSound? = null,
        isFront: Boolean = false
    ) {
        val executor = Executors.newSingleThreadExecutor()
        // 如果是前置镜头，文件名前加上"—F"后缀，否则加上"—B"后缀
        val suffix = if (isFront) "-F" else "-B"
        val photoFile = File(context.filesDir, "${System.currentTimeMillis()}$suffix.jpg")
        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile)
                .build()
        // 播放快门音效
        shutterSound?.play()
        imageCapture.takePicture(
            outputOptions, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("takePictures", "Image saved to ${photoFile.absolutePath}")
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("takePictures", "Image capture failed", exception)
                }
            }
        )
    }
}