app 是一个基于 Android 平台的相机应用，它提供了连续拍摄不同焦距照片的功能。
这里共有两个用户界面，一个是主界面MainSurface，一个是设置界面SettingsSurface。
主界面用于连续拍摄不同焦距照片，设置界面用于设置相机参数。
主界面包含一个相机预览界面和快门按钮、Zoom调整Slider。
设置界面包含一个焦距列表，用户可以在列表中选择是否使用这个焦距，也可以修改焦距值。
app基于androidx.camera实现相机功能，通过LifecycleCameraProvider获取相机实例，
通过CameraControl控制相机参数，通过CameraPreview显示相机预览，通过ImageCapture捕获照片。
为了实现手动调整焦距，app通过CameraControl访问Camera2CameraControl设定焦距。


### 项目概述
DeepCamera 是一个基于 Android 平台的相机应用项目，
采用 Kotlin 语言和 Jetpack Compose 框架开发，
集成 CameraX 库实现相机功能。项目包含三个独立模块，
分别提供连续变焦距相机、手动控制相机和双摄像头并发采集功能。
### 系统架构
项目采用模块化架构设计，整体结构如下：

```language  
DeepCamera/  
├── app/                 # 连续变焦距相机模块
├── manual_camera/       # 手动控制相机模块  
└── dual_camera/         # 双摄像头并发采集模块  
  
```
各模块均遵循 MVVM 架构模式，核心组件包括：
- UI 层：使用 Jetpack Compose 构建响应式界面
- 相机控制层：基于 CameraX 实现相机硬件交互
- 数据层：处理图片存储与系统媒体库交互

### 主要功能模块
1. 连续变焦点相机相机模块（app）
- 实现相机预览、连续变焦距拍照功能
- 支持手动调整zoom
- 编辑管理焦距一览表
2. 手动控制相机模块（manual_camera）
- 提供相机参数手动调节功能 支持曝光、对焦等参数自定义
- 支持前后摄像头切换
3. 双摄像头模块（dual_camera）
- 支持前后摄像头同时预览
- 实现多摄像头并发采集
### 核心实现方法
1. 相机功能实现



```kotlin
// 相机预览实现（示例代码）
AndroidView(
    factory = { context ->
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }.also { previewView ->
            cameraController.bindToLifecycle(lifecycleOwner, cameraSelector, previewView)
        }
    }
)
```

2. 拍照功能实现



```kotlin
// 拍照逻辑（Util.kt）
private fun takePicture(
    imageCapture: ImageCapture,
    context: Context,
    onImageSaved: () -> Unit
) {
    val outputFileOptions = getOutputFileOptions(context)
    imageCapture.takePicture(
        outputFileOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // 图片保存成功处理
                onImageSaved()
            }
            override fun onError(exception: ImageCaptureException) {
                // 错误处理
            }
        }
    )
}
```

3. 照片存储实现



```kotlin
// 图片文件保存（Util.kt）
private fun getOutputFileOptions(context: Context): ImageCapture.OutputFileOptions {
    val contentValues = ContentValues().apply {
        val currentDateTime = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${currentDateTime}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }
    return ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, 
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
        contentValues
    ).build()
}
```

4. 权限管理



```kotlin
// 相机权限请求（MainActivity.kt）
private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        // 权限授予，初始化相机
    } else {
        // 权限拒绝，显示提示
    }
}
```

### 技术栈
- 开发语言：Kotlin
- UI 框架：Jetpack Compose
- 相机库：CameraX
- 架构组件：ViewModel、Lifecycle、Navigation
- 并发处理：Kotlin Coroutines
- 图片存储：MediaStore
