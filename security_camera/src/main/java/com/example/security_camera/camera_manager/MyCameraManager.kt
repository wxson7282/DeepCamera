package com.example.security_camera.camera_manager

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
import android.os.CountDownTimer
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.Delegates

/**
 * MyCameraManager - 精简版主管理类
 *
 * 职责：
 * 1. CameraX 初始化和生命周期管理
 * 2. Preview + ImageAnalysis UseCase 配置
 * 3. 录制控制逻辑
 * 4. 存储空间管理
 * 5. 协调 VideoEncoder 和 WatermarkOverlay 模块工作
 *
 * 模块依赖关系：
 * - VideoEncoder: 负责 H.264 编码和 MP4 封装
 * - WatermarkOverlay: 负责在 YUV 数据上叠加水印
 * - YuvUtils: YUV 格式处理工具（可选）
 */
class MyCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val sharedPreferences: SharedPreferences? = null
) {
    companion object {
        private const val TAG = "MyCameraManager"

    }

    // ==================== 配置参数 ====================
    private var maxStorageSize by Delegates.notNull<Long>()
    private var videoCaptureQuality by Delegates.notNull<String>()
    private var videoClipLength by Delegates.notNull<Long>()
    private var strVideoFps by Delegates.notNull<String>()
    private var videoWidth by Delegates.notNull<Int>()
    private var videoHeight by Delegates.notNull<Int>()
    private var frameRate by Delegates.notNull<Int>()

    // ==================== CameraX 组件 ====================
    private val videoStorageDir by lazy {
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.apply {
            if (!exists()) mkdirs()
        } ?: File(context.filesDir, "Movies").apply {
            if (!exists()) mkdirs()
        }
    }

    val previewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraPreview by Delegates.notNull<Preview>()
    private var imageAnalysis by Delegates.notNull<ImageAnalysis>()

    // ==================== 录制状态 ====================
    private var recordingTimer by Delegates.notNull<CountDownTimer>()
    private val isRecording = AtomicBoolean(false)
    private val recordingStartTime = AtomicLong(0)

    // ==================== 帧率控制 ====================
    private var lastEncodedFrameTime = AtomicLong(0)  // 上一帧编码时的系统时间
    private var frameIntervalMs = 0L                   // 目标帧间隔（毫秒）

    // ==================== 编码器和水印模块 ====================
    private var videoEncoder: VideoEncoder? = null
    private var watermarkOverlay: WatermarkOverlay? = null
    private var encoderThread: HandlerThread? = null
    private var encoderHandler: Handler? = null
    // ==================== 初始化 ====================
    private var yuvInfo: String? = null

    /**
     * 加载配置参数
     */
    private fun loadParameters() {
        maxStorageSize = (sharedPreferences?.getInt("storage_space", 5) ?: 5) * 1024 * 1024 * 1024L
        videoCaptureQuality = sharedPreferences?.getString("video_quality", "SD") ?: "SD"
        videoClipLength = (sharedPreferences?.getInt("video_clip_length", 5) ?: 5) * 60 * 1000L
        strVideoFps = sharedPreferences?.getString("video_fps", "30-30") ?: "30-30"

        // 解析分辨率
        val (_, width, height) = when (videoCaptureQuality) {
            "HD" -> Triple(Quality.HD, 1280, 720)
            "FHD" -> Triple(Quality.FHD, 1920, 1080)
            else -> Triple(Quality.SD, 640, 480)
        }
        videoWidth = width
        videoHeight = height

        // 解析帧率
        frameRate = strVideoFps.split("-").let { parts ->
            parts.getOrNull(0)?.toIntOrNull() ?: 30
        }
        // 计算目标帧间隔（毫秒），用于丢帧控制，减去16ms（编码时间）
        frameIntervalMs = 1000L / frameRate - 16L

        // 初始化 CameraX 组件
        cameraPreview = createPreview().apply {
            // 使用previewView作为预览表面
            setSurfaceProvider(previewView.surfaceProvider)
        }
        imageAnalysis = createImageAnalysis()

        // 初始化水印模块
        watermarkOverlay = WatermarkOverlay(videoWidth, videoHeight)

        // 创建录制定时器
        recordingTimer = createRecordingTimer()
    }

    /**
     * 初始化相机
     */
    fun initCamera() {
        loadParameters()
        cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases()
    }

    /**
     * 释放所有资源
     */
    fun release() {
        stopRecord()
        cameraProvider?.unbindAll()
        releaseEncoder()
        watermarkOverlay?.release()
        watermarkOverlay = null
    }

    // ==================== CameraX 组件创建 ====================
    @OptIn(ExperimentalCamera2Interop::class)
    private fun createPreview(): Preview {
        // 设置预览分辨率
        val resolutionStrategy = ResolutionStrategy(
            Size(videoWidth, videoHeight),
            FALLBACK_RULE_CLOSEST_LOWER
        )
        // 设置预览比例
        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_AUTO
        )
        // 创建预览选择器
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .setResolutionStrategy(resolutionStrategy)
            .build()
        // 创建 Preview 实例
        return Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
    }

    @OptIn(ExperimentalGetImage::class)
    private fun createImageAnalysis(): ImageAnalysis {
        val resolutionStrategy = ResolutionStrategy(
            Size(videoWidth, videoHeight),
            FALLBACK_RULE_CLOSEST_LOWER
        )
        val aspectRatioStrategy = AspectRatioStrategy(
            AspectRatio.RATIO_4_3,
            AspectRatioStrategy.FALLBACK_RULE_AUTO
        )
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(aspectRatioStrategy)
            .setResolutionStrategy(resolutionStrategy)
            .build()

        return ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    // 打印 YUV 信息
                    if (yuvInfo == null) {
                        val image = imageProxy.image
                        if (image != null) {
                            yuvInfo = " Image Format:" + image.format.toString() + "\n" +
                                    " Image Width:" + image.width.toString() + "\n" +
                                    " Image Height:" + image.height.toString() + "\n"
                            image.planes.forEachIndexed { index, it ->
                                yuvInfo += " Plane${index} Size:" + it.buffer.capacity().toString() +
                                        " pixelStride:" + it.pixelStride.toString() +
                                        " rowStride:" + it.rowStride.toString() + "\n"
                            }
                        }
                        Log.i(TAG, "yuvInfo: ${yuvInfo ?: ""}")
                    }

                    processFrame(imageProxy)
                }
            }
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                cameraPreview,
                imageAnalysis
            )
            Log.i(TAG, "相机绑定成功")
        } catch (e: CameraInfoUnavailableException) {
            Log.e(TAG, "无法获取相机信息", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "相机状态异常", e)
        } catch (e: Exception) {
            Log.e(TAG, "绑定相机用例失败", e)
        }
    }

    // ==================== 帧处理 ====================

    /**
     * 处理 ImageAnalysis 获取的每一帧
     */
    @ExperimentalGetImage
    private fun processFrame(imageProxy: ImageProxy) {
        if (!isRecording.get()) {
            imageProxy.close()
            return
        }

        // ★ 帧率控制：按目标帧间隔丢帧
        // 考虑处理所需时间，如果帧率大于20fps，不做任何处理
        if (frameRate < 20) {
            val now = System.currentTimeMillis()
            val lastTime = lastEncodedFrameTime.get()
            if (lastTime > 0 && (now - lastTime) < frameIntervalMs) {
                imageProxy.close()
                return  // 距离上一帧不够一个帧间隔，丢弃
            }
            lastEncodedFrameTime.set(now)
        }

        try {
            val image = imageProxy.image ?: run {
                imageProxy.close()
                return
            }

            // ★ 水印时间直接用系统当前时间
            // imageInfo.timestamp 是相机传感器时钟（类似 System.nanoTime()，从开机算起），
            // 不是 Unix 时间戳，不能加到 System.currentTimeMillis() 上
            val watermarkTime = System.currentTimeMillis()

            // 生成水印文本
            val watermarkText = watermarkOverlay?.formatTimestamp(watermarkTime) ?: return

            // 获取实际图像分辨率
            val actualWidth = imageProxy.width
            val actualHeight = imageProxy.height
            watermarkOverlay?.updateDimensions(actualWidth, actualHeight)

            // 提取 YUV 数据
            val yuvData = YuvUtils.extractYUV420SPFromImage(image) ?: run {
                imageProxy.close()
                return
            }

            // 叠加水印
            val watermarkedYuv = watermarkOverlay?.overlayWatermark(yuvData, watermarkText) ?: yuvData

            val presentationTimeNs = System.nanoTime()
            videoEncoder?.encodeFrame(watermarkedYuv, presentationTimeNs)

        } catch (e: Exception) {
            Log.e(TAG, "处理帧失败", e)
        } finally {
            imageProxy.close()
        }
    }

    // ==================== 录制控制 ====================

    /**
     * 创建录制定时器（用于分段录制）
     */
    private fun createRecordingTimer(): CountDownTimer {
        return object : CountDownTimer(videoClipLength, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                Log.i(TAG, "视频片段时长: ${millisUntilFinished / 1000} 秒")
            }

            override fun onFinish() {
                // 片段录制完成，重新开始新片段
                stopRecord()
                startRecord()
            }
        }
    }

    /**
     * 开始视频录制
     */
    fun startRecord() {
        Log.i(TAG, "startRecord() 开始")

        // 确保存储空间
        ensureStorageSpace()

        // 创建视频文件
        val sdf = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault())
        val formattedDateTime = sdf.format(Date())
        val videoFile = File(
            videoStorageDir, "SECURITY_${formattedDateTime}.mp4"
        )
        Log.i(TAG, "视频文件: ${videoFile.absolutePath}")

        // 启动编码线程
        encoderThread = HandlerThread("VideoEncoderThread").apply {
            start()
            encoderHandler = Handler(looper)
        }

        // 计算码率
        val bitRate = calculateBitRate()

        // 在编码线程中初始化编码器
        encoderHandler?.post {
            videoEncoder = VideoEncoder(
                width = videoWidth,
                height = videoHeight,
                frameRate = frameRate,
                bitRate = bitRate,
                outputFile = videoFile
            )
        }

        // 设置录制状态
        recordingStartTime.set(System.currentTimeMillis())
        isRecording.set(true)

        // 启动定时器
        recordingTimer.cancel()
        recordingTimer.start()

        Log.i(TAG, "startRecord() 完成")
    }

    /**
     * 停止视频录制
     */
    fun stopRecord() {
        Log.i(TAG, "stopRecord() 开始")

        recordingTimer.cancel()
        isRecording.set(false)

        // 停止编码器
        videoEncoder?.stop()
        videoEncoder?.release()
        videoEncoder = null

        // 停止编码线程
        encoderThread?.quitSafely()
        encoderThread = null
        encoderHandler = null

        recordingStartTime.set(0)

        Log.i(TAG, "stopRecord() 完成")
    }

    /**
     * 计算视频码率
     */
    private fun calculateBitRate(): Int {
        return when (videoCaptureQuality) {
            "FHD" -> 8_000_000  // 8 Mbps
            "HD" -> 4_000_000   // 4 Mbps
            else -> 2_000_000   // 2 Mbps
        }
    }

    /**
     * 释放编码器资源
     */
    private fun releaseEncoder() {
        videoEncoder?.stop()
        videoEncoder?.release()
        videoEncoder = null

        encoderThread?.quitSafely()
        encoderThread = null
        encoderHandler = null
    }

    // ==================== 存储管理 ====================

    /**
     * 确保有足够的存储空间
     */
    private fun ensureStorageSpace() {
        Log.i(TAG, "检查存储空间")
        var currentUsage = getCurrentStorageUsage()

        while (currentUsage > maxStorageSize) {
            val oldestFile = getOldestVideoFile() ?: break
            val fileSize = oldestFile.length()

            if (oldestFile.delete()) {
                Log.i(TAG, "删除文件: ${oldestFile.name}, 大小: ${fileSize / 1024 / 1024}MB")
                currentUsage -= fileSize
            } else {
                Log.e(TAG, "无法删除文件: ${oldestFile.name}")
                break
            }
        }
    }

    /**
     * 获取当前存储使用量
     */
    private fun getCurrentStorageUsage(): Long {
        return videoStorageDir.listFiles { file ->
            file.isFile && file.name.startsWith("SECURITY_") && file.name.endsWith(".mp4")
        }?.sumOf { it.length() } ?: 0L
    }

    /**
     * 获取最旧的视频文件
     */
    private fun getOldestVideoFile(): File? {
        return videoStorageDir.listFiles { file ->
            file.isFile && file.name.startsWith("SECURITY_") && file.name.endsWith(".mp4")
        }?.minByOrNull {
            it.name.substringAfter("SECURITY_").substringBefore(".mp4").toLongOrNull() ?: Long.MAX_VALUE
        }
    }

    // ==================== 公开方法 ====================

    /**
     * 获取支持的帧率范围
     */
    @OptIn(ExperimentalCamera2Interop::class)
    fun getFpsRanges(): Array<Range<Int>> {
        val cam = camera ?: return arrayOf(Range(30, 30))
        return Camera2CameraInfo.from(cam.cameraInfo).getCameraCharacteristic(
            CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
        ) ?: arrayOf(Range(30, 30))
    }

}
