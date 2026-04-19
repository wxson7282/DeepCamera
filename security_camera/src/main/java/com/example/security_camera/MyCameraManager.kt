package com.example.security_camera

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
import android.hardware.camera2.CaptureRequest
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executors
import kotlin.properties.Delegates

class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val sharedPreferences: SharedPreferences? = null
) {
    // 最大存储空间
    private var maxStorageSize by Delegates.notNull<Long>()
    // 视频录制质量
    private var videoCaptureQuality by Delegates.notNull<String>()
    // 视频片段时长(分钟转毫秒)
    private var videoClipLength by Delegates.notNull<Long>()
    // 视频帧率
    private var strVideoFps by Delegates.notNull<String>()

    private val videoStorageDir by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.apply {
            if (!exists()) mkdirs() // 确保目录存在
        } ?: File(context.filesDir, "Movies").apply {
            if (!exists()) mkdirs()
        }
    }
    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
    }
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var capture by Delegates.notNull<VideoCapture<Recorder>>()
    private var cameraPreview  by Delegates.notNull<Preview>()

    // 视频录制事件监听
    private val recordEventListener = Consumer<VideoRecordEvent> { videoRecordEvent ->
        when (videoRecordEvent) {
            is VideoRecordEvent.Start -> Log.i("CameraManager", "视频录制开始")
            is VideoRecordEvent.Pause -> Log.i("CameraManager", "视频录制暂停")
            is VideoRecordEvent.Resume -> Log.i("CameraManager", "视频录制继续")
            is VideoRecordEvent.Finalize -> {
                Log.i("CameraManager", "视频录制结束")
                val error = videoRecordEvent.error
                if (error == VideoRecordEvent.Finalize.ERROR_NONE) {
                    Log.i("CameraManager", "视频录制成功")
                } else {
                    Log.e("CameraManager", "视频录制失败", videoRecordEvent.cause)
                }
            }
        }
    }

    // 添加录制状态跟踪变量
    private var recording: Recording? = null

    // 添加定时器变量
    private var recordingTimer by Delegates.notNull<CountDownTimer>()

    private fun loadParameters() {
        // 加载参数
        maxStorageSize =
            (sharedPreferences?.getInt("storage_space", 5) ?: 5) * 1024 * 1024 * 1024L
        videoCaptureQuality = sharedPreferences?.getString("video_quality", "SD") ?: "SD"
        videoClipLength = (sharedPreferences?.getInt("video_clip_length", 5) ?: 5) * 60 * 1000L
        strVideoFps = sharedPreferences?.getString("video_fps", "30-30") ?: "30-30"
        // 初始化使用上述参数的相关变量
        capture = getVideoCapture()
        cameraPreview = getPreview().apply {
            Log.i("CameraManager", "getPreview()")
            setSurfaceProvider(previewView.surfaceProvider)
            Log.i("CameraManager", "setSurfaceProvider()")
        }
        recordingTimer = object : CountDownTimer(videoClipLength, 1000) {

            override fun onTick(millisUntilFinished: Long) {
                Log.i("CameraManager", "视频片段时长: ${millisUntilFinished / 1000} 秒")
            }

            override fun onFinish() {
                // 视频片段录制完成，开始新的录制
                stopRecord()
                startRecord()
            }
        }

    }

    fun initCamera() {
        loadParameters()
        cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases()
    }

    fun release() {
        cameraProvider?.unbindAll()
    }

    /**
     * 开始视频录制
     */
    fun startRecord() {
        Log.i("CameraManager", "startRecord() is going")
        // 确保有足够的存储空间，删除最早文件（如果需要）
        ensureStorageSpace()
        // 创建视频存储文件 (使用时间戳确保唯一性)
        val videoFile = File(
            videoStorageDir, "SECURITY_${System.currentTimeMillis()}.mp4"
        )
        Log.i("CameraManager", "startRecord() videoFile = $videoFile")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        try {
            // 启动录制
            recording = capture.output.prepareRecording(context, outputOptions).start(
                    Executors.newSingleThreadExecutor()
                ) { recordEventListener }
            // 启动录制定时器
            Log.i("CameraManager", "recordingTimer.start()")
            recordingTimer.cancel()
            recordingTimer.start()
        } catch (e: Exception) {
            Log.e("CameraManager", "视频录制失败", e)
        }
    }

    /**
     * 停止视频录制
     */
    fun stopRecord() {
        recordingTimer.cancel()
        recording?.stop()
        recording = null
    }

    fun turnOnScreen() {}
    fun turnOffScreen() {}

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getVideoCapture(): VideoCapture<Recorder> {
        val recorder =
            Recorder.Builder().setExecutor(Executors.newSingleThreadExecutor()).setQualitySelector(
                    when (videoCaptureQuality) {
                        "HD" -> QualitySelector.from(Quality.HD)
                        "FHD" -> QualitySelector.from(Quality.FHD)
                        else -> QualitySelector.from(Quality.SD)
                    }
                ).build()
        val videoCaptureBuilder = VideoCapture.Builder(recorder)
        // 解析视频帧率字符串为Range<Int>对象
        val fpsRange: Range<Int> = strVideoFps.split("-").let { parts ->
            Range(parts[0].toInt(), parts[1].toInt())
        }
        Log.i("CameraManager", "fpsRange = $strVideoFps")
        Camera2Interop.Extender(videoCaptureBuilder).setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange
            )
        return videoCaptureBuilder.build()
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getPreview(): Preview {
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

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, capture, cameraPreview
            )
        } catch (e: CameraInfoUnavailableException) {
            Log.e("CameraManager", "无法获取相机信息", e)
        } catch (e: IllegalStateException) {
            Log.e("CameraManager", "相机状态异常", e)
        } catch (e: Exception) {
            Log.e("CameraManager", "Error binding camera use cases", e)
        }
    }

    private fun ensureStorageSpace() {
        Log.i("CameraManager", "ensureStorageSpace() is going")
        var currentUsageSize = getCurrentStorageUsage()
        // 如果当前使用空间超过最大限制，删除最早的文件
        while (currentUsageSize > maxStorageSize) {
            val oldestFile = getOldestVideoFile() ?: break // 没有可删除的文件
            val fileSize = oldestFile.length()

            if (oldestFile.delete()) {
                Log.i(
                    "CameraManager",
                    "删除 oldest file: ${oldestFile.name}, size: ${fileSize / 1024 / 1024}MB"
                )
                currentUsageSize -= fileSize
            } else {
                Log.e("CameraManager", "无法删除文件: ${oldestFile.name}")
                break
            }
        }
    }

    /**
     * 计算当前存储目录占用的总空间
     */
    private fun getCurrentStorageUsage(): Long {
        return videoStorageDir.listFiles { file ->
            file.isFile && file.name.startsWith("SECURITY_") && file.name.endsWith(".mp4")
        }?.sumOf { it.length() } ?: 0L
    }

    /**
     * 获取最早创建的视频文件
     */
    private fun getOldestVideoFile(): File? {
        return videoStorageDir.listFiles { file ->
            file.isFile && file.name.startsWith("SECURITY_") && file.name.endsWith(".mp4")
        }?.minByOrNull {
            // 从文件名提取时间戳
            it.name.substringAfter("SECURITY_").substringBefore(".mp4").toLongOrNull()
                ?: Long.MAX_VALUE // 无法解析时间戳的文件视为最新
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun getFpsRanges(): Array<Range<Int>> {
        // 获取支持的帧率范围，默认返回 30fps
        val camera = camera ?: return arrayOf(Range(30, 30))
        val supportedFpsRanges = Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(
            CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ) ?: arrayOf(Range(30, 30))
        return supportedFpsRanges
    }

}