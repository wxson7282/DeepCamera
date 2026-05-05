package com.example.security_camera.camera_manager

import android.media.Image
import android.util.Log

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
                // 逐采样从 plane[1] 读 U，从 plane[2] 读 V，交织为 UV
                // 这样无论设备底层是 NV21 还是 NV12，输出都是正确的 YUV420SP
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

}
