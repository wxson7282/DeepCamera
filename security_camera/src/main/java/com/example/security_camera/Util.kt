package com.example.security_camera

import android.content.Context
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture


object Util {

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

    fun getVideoCapture(): VideoCapture<Recorder> {
        return VideoCapture.withOutput<Recorder>(getRecorder())
    }

    private fun getRecorder(): Recorder {
        return Recorder.Builder().build()
    }

    fun recordVideos(
        context: Context,
        videoCapture: VideoCapture<Recorder>,
        camera: Camera
    ) {
//        // 定义视频捕获文件路径
//        val videoFile = File(context.filesDir, "video.mp4")
//        // 定义视频捕获输出配置
//        val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile).build()
//        // 启动视频捕获
//        videoCapture.start(outputOptions)
    }
}