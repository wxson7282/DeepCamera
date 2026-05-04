package com.example.security_camera.camera_manager

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage

/**
 * YUV 工具类
 *
 * 职责：
 * 1. YUV 格式检测和转换
 * 2. ImageProxy 到 ByteArray 的转换
 * 3. YUV420 平面数据提取（精确处理 rowStride/pixelStride）
 * 4. YUV NV21/NV12/YV12 格式处理
 */
object YuvUtils {

    private const val TAG = "YuvUtils"

    /**
     * 从 ImageProxy 提取 NV21 数据
     *
     * CameraX 的 YUV_420_888 大多数设备实际是 NV21 半平面格式（pixelStride=2）。
     * 此方法直接输出 NV21 字节流（Y + VU交织），适合传给 MediaCodec 编码。
     *
     * @param imageProxy CameraX ImageProxy
     * @return NV21 格式的字节数组，失败返回 null
     */
    @OptIn(ExperimentalGetImage::class)
    fun extractYUV420SPFromImageProxy(imageProxy: androidx.camera.core.ImageProxy): ByteArray? {
        return try {
            val image = imageProxy.image ?: return null
            extractYUV420SPFromImage(image)
        } catch (e: Exception) {
            Log.e(TAG, "提取 NV21 数据失败", e)
            null
        }
    }

    /**
     * 从 Image 提取 YUV420SP 数据（Y + UV 交织）
     *
     * 半平面格式下，plane[1] (U) 的 buffer 包含原始 U 分量，格式是{U_U_U_U...U_U}（少最后 1 字节），
     *            plane[2] (V) 的 buffer 包含原始 V 分量，格式是{V_V_V_V...V_V}（少最后 1 字节）。
     * 返回值：YUV420SP 格式的字节数组{Y0 Y1 ... Yn | U0 V0 U1 V1 ... Un Vn}
     */
    fun extractYUV420SPFromImage(image: Image): ByteArray? {
        return try {
            val width = image.width
            val height = image.height
            val planes = image.planes

            val ySize = width * height
            val vuSize = ySize / 2
            val yuv420SP = ByteArray(ySize + vuSize)

            // --- Y 平面 ---
            val yPlane = planes[0]
            val yBuffer = yPlane.buffer
            val yRowStride = yPlane.rowStride

            if (yRowStride == width) { // 完整行对齐
                yBuffer.position(0)
                yBuffer.get(yuv420SP, 0, ySize)
            } else { // 非完整行对齐
                for (row in 0 until height) { // 逐行读取
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(yuv420SP, row * width, width)
                }
            }

            // --- 色度平面 ---
            // Android 约定：plane[1] = Cb(U), plane[2] = Cr(V).
            val vPlane = planes[2]  // Cr (V)
            val uPlane = planes[1]  // Cb (U)
            val vBuffer = vPlane.buffer
            val uBuffer = uPlane.buffer
            val uvPixelStride = vPlane.pixelStride
            val uvRowStride = vPlane.rowStride
            val uvHeight = height / 2
            val uvWidth = width / 2

            if (uvPixelStride == 2) {
                // 半平面交织格式
                // 逐采样从 plane[2] 读 V，从 plane[1] 读 U，交织为 VU
                // 这样无论设备底层是 NV21 还是 NV12，输出都是正确的 NV21
                for (row in 0 until uvHeight) {
                    val rowOffset = row * uvRowStride
                    val dstRowOffset = ySize + row * width
                    for (col in 0 until uvWidth) {
                        val pos = rowOffset + col * uvPixelStride
                        yuv420SP[dstRowOffset + col * 2] = uBuffer.get(pos)      // u
                        yuv420SP[dstRowOffset + col * 2 + 1] = vBuffer.get(pos)  // v
                    }
                }
            } else {
                // pixelStride=1：独立平面，逐采样交织为 VU
                for (row in 0 until uvHeight) {
                    val rowOffset = row * uvRowStride
                    val dstRowOffset = ySize + row * width
                    for (col in 0 until uvWidth) {
                        yuv420SP[dstRowOffset + col * 2] = uBuffer.get(rowOffset + col)      // u
                        yuv420SP[dstRowOffset + col * 2 + 1] = vBuffer.get(rowOffset + col)  // v
                    }
                }
            }

            yuv420SP
        } catch (e: Exception) {
            Log.e(TAG, "提取 NV21 数据失败", e)
            null
        }
    }

    /**
     * 从 Image 提取 YUV420 平面数据（Y-U-V 分离排列）
     *
     * 精确处理 rowStride padding 和 pixelStride 交织，
     * 输出紧凑的 Y-U-V 三段连续字节数组。
     *
     * @param image Android Image
     * @return YUV420 格式的字节数组（Y-U-V 连续排列），失败返回 null
     */
    fun extractYuv420FromImage(image: Image): ByteArray? {
        return try {
            val width = image.width
            val height = image.height
            val planes = image.planes

            val ySize = width * height
            val uvSize = ySize / 4
            val yuv420 = ByteArray(ySize + uvSize * 2)

            // --- Y 平面 ---
            val yPlane = planes[0]
            val yRowStride = yPlane.rowStride
            val yBuffer = yPlane.buffer

            if (yRowStride == width) {
                yBuffer.get(yuv420, 0, ySize)
            } else {
                for (row in 0 until height) {
                    yBuffer.position(row * yRowStride)
                    yBuffer.get(yuv420, row * width, width)
                }
            }

            // --- 色度平面 ---
            val uPlane = planes[1]
            val vPlane = planes[2]
            val uvPixelStride = uPlane.pixelStride
            val uvRowStride = uPlane.rowStride
            val uvHeight = height / 2
            val uvWidth = width / 2

            if (uvPixelStride == 2) {
                // 半平面交织格式，需要分离 U 和 V
                val uBuffer = uPlane.buffer
                val vBuffer = vPlane.buffer

                var uIndex = ySize
                var vIndex = ySize + uvSize

                if (uvRowStride == width) {
                    // 无 padding，连续读取
                    for (row in 0 until uvHeight) {
                        for (col in 0 until uvWidth) {
                            // pixelStride=2 时，每隔一个字节取一个
                            // plane[1] 的首字节：多数设备是 V（NV21），少数是 U（NV12）
                            // 这里统一按 plane 索引分离
                            val rowOffset = row * uvRowStride + col * uvPixelStride
                            yuv420[uIndex++] = uPlane.buffer.get(rowOffset)
                            yuv420[vIndex++] = vPlane.buffer.get(rowOffset)
                        }
                    }
                } else {
                    // 有 padding
                    for (row in 0 until uvHeight) {
                        for (col in 0 until uvWidth) {
                            val rowOffset = row * uvRowStride + col * uvPixelStride
                            yuv420[uIndex++] = uPlane.buffer.get(rowOffset)
                            yuv420[vIndex++] = vPlane.buffer.get(rowOffset)
                        }
                    }
                }
            } else {
                // 独立平面格式（pixelStride=1），直接逐行拷贝
                val uBuffer = uPlane.buffer
                val vBuffer = vPlane.buffer

                if (uvRowStride == uvWidth) {
                    uBuffer.get(yuv420, ySize, uvSize)
                    vBuffer.get(yuv420, ySize + uvSize, uvSize)
                } else {
                    for (row in 0 until uvHeight) {
                        uBuffer.position(row * uvRowStride)
                        uBuffer.get(yuv420, ySize + row * uvWidth, uvWidth)
                        vBuffer.position(row * uvRowStride)
                        vBuffer.get(yuv420, ySize + uvSize + row * uvWidth, uvWidth)
                    }
                }
            }

            yuv420
        } catch (e: Exception) {
            Log.e(TAG, "提取 YUV420 数据失败", e)
            null
        }
    }

    /**
     * 从 ImageProxy 提取 YUV420 平面数据
     *
     * @param imageProxy CameraX ImageProxy
     * @return YUV420 格式的字节数组（Y-U-V 连续排列），失败返回 null
     */
    @OptIn(ExperimentalGetImage::class)
    fun extractYuv420FromImageProxy(imageProxy: androidx.camera.core.ImageProxy): ByteArray? {
        return try {
            val image = imageProxy.image ?: return null
            extractYuv420FromImage(image)
        } catch (e: Exception) {
            Log.e(TAG, "提取 YUV420 数据失败", e)
            null
        }
    }

    /**
     * NV21 转 YUV420（Y-U-V 平面分离）
     *
     * @param nv21Data NV21 格式数据
     * @param width 宽度
     * @param height 高度
     * @return YUV420 格式数据
     */
    fun nv21ToYuv420(nv21Data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4

        val yuv420 = ByteArray(ySize + uvSize * 2)

        // 复制 Y
        System.arraycopy(nv21Data, 0, yuv420, 0, ySize)

        // NV21: VU interleaved (V first), 分离为 U 和 V
        val vuOffset = ySize
        var uIndex = ySize
        var vIndex = ySize + uvSize
        for (i in 0 until uvSize) {
            if (i % 2 == 0) {
                yuv420[vIndex++] = nv21Data[vuOffset + i]  // V
            } else {
                yuv420[uIndex++] = nv21Data[vuOffset + i]  // U
            }
        }

        return yuv420
    }

    /**
     * NV12 转 YUV420（Y-U-V 平面分离）
     *
     * @param nv12Data NV12 格式数据
     * @param width 宽度
     * @param height 高度
     * @return YUV420 格式数据
     */
    fun nv12ToYuv420(nv12Data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4

        val yuv420 = ByteArray(ySize + uvSize * 2)

        // 复制 Y
        System.arraycopy(nv12Data, 0, yuv420, 0, ySize)

        // NV12: UV interleaved (U first), 分离为 U 和 V
        val uvOffset = ySize
        var uIndex = ySize
        var vIndex = ySize + uvSize
        for (i in 0 until uvSize) {
            if (i % 2 == 0) {
                yuv420[uIndex++] = nv12Data[uvOffset + i]  // U
            } else {
                yuv420[vIndex++] = nv12Data[uvOffset + i]  // V
            }
        }

        return yuv420
    }

    /**
     * YV12 转 YUV420（Y-U-V 平面分离）
     *
     * @param yv12Data YV12 格式数据
     * @param width 宽度
     * @param height 高度
     * @return YUV420 格式数据
     */
    fun yv12ToYuv420(yv12Data: ByteArray, width: Int, height: Int): ByteArray {
        val ySize = width * height
        val uvSize = ySize / 4

        val yuv420 = ByteArray(ySize + uvSize * 2)

        // 复制 Y
        System.arraycopy(yv12Data, 0, yuv420, 0, ySize)

        // YV12: V 在 U 之前，交换顺序
        System.arraycopy(yv12Data, ySize + uvSize, yuv420, ySize, uvSize)       // U
        System.arraycopy(yv12Data, ySize, yuv420, ySize + uvSize, uvSize)       // V

        return yuv420
    }

    /**
     * 检测 Image 的 YUV 格式
     *
     * @param image Android Image
     * @return YUV 格式类型
     */
    fun detectYuvFormat(image: Image): YuvFormat {
        if (image.format == ImageFormat.YV12) return YuvFormat.YV12
        if (image.format == ImageFormat.NV21) return YuvFormat.NV21
        if (image.format != ImageFormat.YUV_420_888) return YuvFormat.UNKNOWN

        // YUV_420_888 是容器格式，需要通过 planes 排列判断实际格式
        val planes = image.planes
        if (planes.size != 3) return YuvFormat.YUV_420_888

        val uvPixelStride = planes[1].pixelStride

        return when {
            uvPixelStride == 2 -> {
                // 半平面格式：plane[1] 首字节是 V 则 NV21，是 U 则 NV12
                // 大多数 Android 设备是 NV21
                YuvFormat.NV21
            }
            uvPixelStride == 1 -> {
                YuvFormat.YUV_420_888  // 独立平面
            }
            else -> YuvFormat.UNKNOWN
        }
    }

    /**
     * YUV 格式枚举
     */
    enum class YuvFormat {
        YUV_420_888,  // 标准格式，Y-U-V 平面分离
        YV12,         // Y-V-U 排列
        NV21,         // Y + VU interleaved
        NV12,         // Y + UV interleaved
        UNKNOWN
    }

    /**
     * 计算 YUV 数据大小
     *
     * @param width 宽度
     * @param height 高度
     * @return YUV420 所需字节数
     */
    fun calculateYuvSize(width: Int, height: Int): Int {
        val ySize = width * height
        val uvSize = ySize / 4
        return ySize + uvSize * 2
    }
}
