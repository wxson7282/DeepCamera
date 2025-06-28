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
        imageCapture: ImageCapture
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val shutterSound = ShutterSound(context)
        val photoFile = File(context.filesDir, "${System.currentTimeMillis()}.jpg")
        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(photoFile).build()
        shutterSound.play()
        imageCapture.takePicture(
            outputOptions, executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i("takePictures", "Image saved to ${photoFile.absolutePath}")
                    shutterSound.release()
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e("takePictures", "Image capture failed", exception)
                    shutterSound.release()
                }
            }
        )
    }
}