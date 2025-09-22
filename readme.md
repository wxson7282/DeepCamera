
# 用androidx.camera拍摄景深合成照片

androidx.camera的不断完善，使得原来复杂繁琐的安卓相机开发容易了许多。很多传统相机上有称之为景深包围的拍照功能，
一次拍摄完成多张不同焦点的照片，后期用软件把多张照片合称为一张大景深或全景深照片。这种拍摄方式在安卓系统中也可以实现。


### 项目概述
DeepCamera 是一个基于 Android 平台的相机应用项目，
采用 Kotlin 语言和 Jetpack Compose 框架开发，
集成 CameraX 库实现相机功能。项目包含三个独立模块，
分别提供连续变焦拍摄、手动控制相机和双摄像头并发采集功能。
### 系统架构
项目采用模块化架构设计，整体结构如下：

```language  
DeepCamera/  
├── app/                 # 连续变焦拍摄模块  
├── manual_camera/       # 手动控制相机模块  
└── dual_camera/         # 双摄像头并发采集模块  
  
```
各模块均遵循 MVVM 架构模式，核心组件包括：
- UI 层：使用 Jetpack Compose 构建响应式界面
- 相机控制层：基于 CameraX 实现相机硬件交互
- 数据层：处理图片存储与系统媒体库交互

### 主要功能模块
1.连续变焦拍摄模块（app）
- 实现相机连续变焦拍摄功能
- 支持照片保存到系统相册
- 自定义焦距一览表
2.手动控制相机模块（manual_camera）
- 提供相机参数手动调节功能 支持曝光、对焦等参数自定义
- 支持前后摄像头控制
3.双摄像头模块（dual_camera）
- 支持前后摄像头同时预览
- 实现多摄像头并发采集
### 相机控制基础概念
androidX用抽象类UseCase（用例）管理相机的应用。
UseCase目前只有ImageAnalysis, ImageCapture, Preview, VideoCapture四种子类，对应四种应用：图像分析、拍照、预览、拍视频。
在程序中需要把UseCase绑定到CameraProvider，以实现对相机应用场景的控制。

androidX对于相机的控制有三种途径
1. CameraController
   CameraController是androidx.camera.view中的抽象类，它的实现是LifecycleCameraController，这是一个高级控制器，
在单个类中提供了大多数 CameraX 核心功能。它负责相机初始化，创建并配置用例，并在准备就绪时将它们绑定到生命周期所有者。
它还会监听设备运动传感器，并为UseCase用例设置目标旋转角度。
2. CameraControl
   CameraControl是androidx.camera.core中的接口，它提供各种异步操作，如变焦、对焦和测光，
这些操作会影响当前绑定到该相机的所有UseCase用例的输出。CameraControl 的每种方法都会返回一个 ListenableFuture，
应用程序可以用它来检查异步结果。
3. Camera2CameraControl
   Camera2CameraControl是androidx.camera.camera2.interop中的不可重写类，
它提供与 android.hardware.camera2 API 的互操作能力，可以实现CameraControl所不能提供的相机底层操作，例如手动设定焦距。

### 核心实现方法
1. 获取Preview
```kotlin
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
```

2.获取ImageCapture
```kotlin
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
```

3.获取ImageCapture的输出文件选项
```kotlin
/**
 * 获取ImageCapture的输出文件选项
 * @param Context 上下文
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
```

4.设置缩放比例
```kotlin
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
```
5.设置焦距

```kotlin
/**
 * 设置焦距
 * @param cameraControl 相机控制
 * @param focusDistance 焦距
 * @return 监听Future
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
```

7.获取相机的对焦范围

```kotlin
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
```

### 技术栈
- 开发语言：Kotlin
- UI 框架：Jetpack Compose
- 相机库：CameraX
- 架构组件：ViewModel、Lifecycle、Navigation
- 并发处理：Kotlin Coroutines
- 图片存储：MediaStore
