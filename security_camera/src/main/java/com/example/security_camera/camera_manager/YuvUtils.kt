package com.example.security_camera.camera_manager

import android.media.Image
import android.util.Log

/**
 * YUV 工具类
 *
 * 职责：
 * 1. YUV 格式检测和转换
 * 2. ImageProxy 到 ByteArray 的转换
 * 3. YUV420 平面数据提取
 * 4. YUV NV21/NV12/YV12 格式处理
 */
object YuvUtils {

    private const val TAG = "YuvUtils"

//    /**
//     * 从 ImageProxy 提取 YUV420_888 数据
//     *
//     * @param imageProxy CameraX ImageProxy
//     * @return YUV420 格式的字节数组（Y-U-V 连续排列）
//     */
//    @ExperimentalGetImage
//    fun extractYuv420FromImageProxy(imageProxy: androidx.camera.core.ImageProxy): ByteArray? {
//        return try {
//            val image = imageProxy.image ?: return null
//
//            val yBuffer = image.planes[0].buffer
//            val uBuffer = image.planes[1].buffer
//            val vBuffer = image.planes[2].buffer
//
//            val ySize = yBuffer.remaining()
//            val uSize = uBuffer.remaining()
//            val vSize = vBuffer.remaining()
//
//            val yuvData = ByteArray(ySize + uSize + vSize)
//
//            // 复制 Y 数据
//            yBuffer.get(yuvData, 0, ySize)
//
//            // 复制 U 数据
//            uBuffer.get(yuvData, ySize, uSize)
//
//            // 复制 V 数据
//            vBuffer.get(yuvData, ySize + uSize, vSize)
//
//            yuvData
//        } catch (e: Exception) {
//            Log.e(TAG, "提取 YUV 数据失败", e)
//            null
//        }
//    }

    /**
     * 从 Image 提取 YUV420_888 数据
     *
     * @param image Android Image
     * @return YUV420 格式的字节数组（Y-U-V 连续排列）
     */
    fun extractYuv420FromImage(image: Image): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val yuvData = ByteArray(ySize + uSize + vSize)

            yBuffer.get(yuvData, 0, ySize)
            uBuffer.get(yuvData, ySize, uSize)
            vBuffer.get(yuvData, ySize + uSize, vSize)

            yuvData
        } catch (e: Exception) {
            Log.e(TAG, "提取 YUV 数据失败", e)
            null
        }
    }

//    /**
//     * YV12 格式转换为 YUV420（Y-U-V 排列）
//     *
//     * 某些设备返回 YV12 格式（Y-V-U），需要转换
//     *
//     * @param yv12Data YV12 格式数据
//     * @param width 宽度
//     * @param height 高度
//     * @return YUV420 格式数据
//     */
//    fun yv12ToYuv420(yv12Data: ByteArray, width: Int, height: Int): ByteArray {
//        val ySize = width * height
//        val uvSize = ySize / 4
//
//        val yuv420 = ByteArray(ySize + uvSize * 2)
//
//        // 复制 Y
//        System.arraycopy(yv12Data, 0, yuv420, 0, ySize)
//
//        // YV12: V 在 U 之前
//        // YUV420: U 在 V 之前
//        System.arraycopy(yv12Data, ySize + uvSize, yuv420, ySize, uvSize) // U
//        System.arraycopy(yv12Data, ySize, yuv420, ySize + uvSize, uvSize) // V
//
//        return yuv420
//    }

//    /**
//     * NV21 格式转换为 YUV420
//     *
//     * NV21: Y plane + VU interleaved (V first)
//     * YUV420: Y-U-V planes
//     *
//     * @param nv21Data NV21 格式数据
//     * @param width 宽度
//     * @param height 高度
//     * @return YUV420 格式数据
//     */
//    fun nv21ToYuv420(nv21Data: ByteArray, width: Int, height: Int): ByteArray {
//        val ySize = width * height
//        val uvSize = ySize / 4
//
//        val yuv420 = ByteArray(ySize + uvSize * 2)
//
//        // 复制 Y
//        System.arraycopy(nv21Data, 0, yuv420, 0, ySize)
//
//        // NV21: VU interleaved (V first)
//        // 需要交换 V 和 U
//        val vuOffset = ySize
//        for (i in 0 until uvSize step 2) {
//            val v = nv21Data[vuOffset + i]
//            val u = nv21Data[vuOffset + i + 1]
//            yuv420[ySize + i] = u       // U
//            yuv420[ySize + uvSize + i] = v  // V
//        }
//
//        return yuv420
//    }

//    /**
//     * NV12 格式转换为 YUV420
//     *
//     * NV12: Y plane + UV interleaved (U first)
//     *
//     * @param nv12Data NV12 格式数据
//     * @param width 宽度
//     * @param height 高度
//     * @return YUV420 格式数据
//     */
//    fun nv12ToYuv420(nv12Data: ByteArray, width: Int, height: Int): ByteArray {
//        val ySize = width * height
//        val uvSize = ySize / 4
//
//        val yuv420 = ByteArray(ySize + uvSize * 2)
//
//        // 复制 Y
//        System.arraycopy(nv12Data, 0, yuv420, 0, ySize)
//
//        // NV12: UV interleaved (U first), 需要分离
//        val uvOffset = ySize
//        for (i in 0 until uvSize step 2) {
//            yuv420[ySize + i] = nv12Data[uvOffset + i]      // U
//            yuv420[ySize + uvSize + i] = nv12Data[uvOffset + i + 1]  // V
//        }
//
//        return yuv420
//    }

//    /**
//     * 检测 Image 的 YUV 格式
//     *
//     * @param image Android Image
//     * @return YUV 格式类型
//     */
//    fun detectYuvFormat(image: Image): YuvFormat {
//        return when (image.format) {
//            ImageFormat.YUV_420_888 -> YuvFormat.YUV_420_888
//            ImageFormat.YV12 -> YuvFormat.YV12
//            ImageFormat.NV21 -> YuvFormat.NV21
//            // NV12 没有官方常量，某些设备可能用 0x3231564E
//            // 通过 planes 排列判断是否为 NV12
//            else -> detectByPlanes(image)
//        }
//    }

//    /**
//     * 通过 planes 排列检测格式
//     * NV12: U 在 V 之前，UV interleaved
//     * NV21: V 在 U 之前，VU interleaved
//     */
//    private fun detectByPlanes(image: Image): YuvFormat {
//        val planes = image.planes
//        if (planes.size != 3) return YuvFormat.UNKNOWN
//
//        val uBuffer = planes[1].buffer
//        val vBuffer = planes[2].buffer
//
//        // 如果 U 和 V 的 buffer 相邻且 interleaved，可能是 NV12 或 NV21
//        return if (uBuffer.remaining() == vBuffer.remaining()) {
//            // 检查 pixelStride 判断是否 interleaved
//            val uPixelStride = planes[1].pixelStride
//            if (uPixelStride == 2) {
//                YuvFormat.NV12  // NV12: UV interleaved
//            } else {
//                YuvFormat.UNKNOWN
//            }
//        } else {
//            YuvFormat.UNKNOWN
//        }
//    }


//    /**
//     * YUV 格式枚举
//     */
//    enum class YuvFormat {
//        YUV_420_888,  // 标准格式，Y-U-V 平面分离
//        YV12,         // Y-V-U 排列
//        NV21,         // Y + VU interleaved
//        NV12,         // Y + UV interleaved
//        UNKNOWN
//    }

//    /**
//     * 计算 YUV 数据大小
//     *
//     * @param width 宽度
//     * @param height 高度
//     * @return YUV420 所需字节数
//     */
//    fun calculateYuvSize(width: Int, height: Int): Int {
//        val ySize = width * height
//        val uvSize = ySize / 4
//        return ySize + uvSize * 2
//    }
}
