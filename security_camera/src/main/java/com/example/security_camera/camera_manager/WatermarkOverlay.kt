package com.example.security_camera.camera_manager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.createBitmap
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 水印绘制模块
 *
 * 职责：
 * 1. 水印配置（字体、大小、位置、透明度）
 * 2. 预渲染 Bitmap 的创建和管理
 * 3. 在 YUV420 数据上叠加水印（Y + UV 平面同时修改）
 * 4. Paint/Canvas 样式配置
 *
 * @param width 视频宽度
 * @param height 视频高度
 */
class WatermarkOverlay(
    initialWidth: Int,
    initialHeight: Int
) {
    private var width = initialWidth
    private var height = initialHeight

    companion object {
        private const val TAG = "WatermarkOverlay"

        // 水印配置常量
        const val TEXT_SIZE_RATIO = 0.025f      // 文字大小占视频高度的比例
        const val MARGIN_RATIO = 0.02f          // 边距占视频宽度的比例
        const val PADDING = 16                  // 水印内边距
        const val ALPHA = 200                   // 水印透明度 (0-255)

        // 时间格式
        var DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    // 水印 Bitmap（预渲染）
    private var watermarkBitmap: Bitmap? = null

    // 水印 Canvas（用于绘制）
    private var watermarkCanvas: Canvas? = null

    // 水印 Paint（文字和背景绘制）
    private var watermarkPaint: Paint? = null

    // 水印绘制区域缓存
    private val watermarkBounds = Rect()

    // 水印锁
    private val lock = Any()

    init {
        initResources()
    }

    /**
     * 初始化水印资源
     */
    private fun initResources() {
        // 创建水印 Paint
        watermarkPaint = Paint().apply {
            color = Color.WHITE
            textSize = (height * TEXT_SIZE_RATIO).coerceAtLeast(24f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }

        // 用模板文字测量，预分配足够大的 Bitmap
        // 模板：最长的日期时间字符串
        val templateText = "0000-00-00 00:00:00"
        val textBounds = Rect()
        watermarkPaint?.getTextBounds(templateText, 0, templateText.length, textBounds)

        // Bitmap 宽度 = 文字宽度 + 内边距 + 额外余量，高度 = 文字高度 + 内边距 + 额外余量
        // 确保不小于实际水印区域
        val bitmapWidth = (textBounds.width() + PADDING * 4).coerceAtMost(width)
        val bitmapHeight = (textBounds.height() + PADDING * 4).coerceAtMost(height)

        // 释放旧的 Bitmap（如果 updateDimensions 重新调用时）
        watermarkBitmap?.recycle()
        watermarkBitmap = createBitmap(bitmapWidth, bitmapHeight)
        watermarkCanvas = Canvas(watermarkBitmap!!)

        Log.i(TAG, "水印资源初始化: videoSize=${width}x${height}, bitmapSize=${bitmapWidth}x${bitmapHeight}")
    }

    /**
     * 更新视频尺寸（实际分辨率可能与配置不同时调用）
     * 会重建所有资源
     */
    fun updateDimensions(newWidth: Int, newHeight: Int) {
        if (width != newWidth || height != newHeight) {
            width = newWidth
            height = newHeight
            initResources()
            Log.i(TAG, "水印尺寸更新: ${newWidth}x${newHeight}")
        }
    }

    /**
     * 在 YUV420SP 数据上叠加时间水印
     *
     * @param yuvData YUV 原始数据（NV12 格式：Y + UV 交织排列）
     * @param watermarkText 水印文本
     * @return 带水印的 YUV 数据
     */
    fun overlayWatermark(yuvData: ByteArray, watermarkText: String): ByteArray {
        val paint = watermarkPaint ?: return yuvData
        val bitmap = watermarkBitmap ?: return yuvData
        val canvas = watermarkCanvas ?: return yuvData

        // 计算水印区域
        val textSize = (height * TEXT_SIZE_RATIO).coerceAtLeast(24f)
        val margin = (width * MARGIN_RATIO).toInt()

        synchronized(lock) {
            paint.textSize = textSize

            // 获取文字边界
            paint.getTextBounds(watermarkText, 0, watermarkText.length, watermarkBounds)

            // 计算水印区域在视频帧中的位置（右下角）
            val watermarkWidth = watermarkBounds.width() + PADDING * 2
            val watermarkHeight = watermarkBounds.height() + PADDING * 2
            val bgLeft = width - watermarkWidth - margin
            val bgTop = height - watermarkHeight - margin

            // ★ 关键修复：在 Bitmap 上用局部坐标绘制，(0,0) 为 Bitmap 左上角
            // 限制绘制区域不超过 Bitmap 大小
            val drawWidth = watermarkWidth.coerceAtMost(bitmap.width)
            val drawHeight = watermarkHeight.coerceAtMost(bitmap.height)

            // 清空 Bitmap
            bitmap.eraseColor(0)

            // 绘制半透明黑色背景（局部坐标）
            paint.color = Color.argb(ALPHA, 0, 0, 0)
            canvas.drawRect(
                0f,
                0f,
                drawWidth.toFloat(),
                drawHeight.toFloat(),
                paint
            )

            // 绘制白色文字（局部坐标）
            paint.color = Color.argb(ALPHA, 255, 255, 255)
            canvas.drawText(
                watermarkText,
                PADDING.toFloat(),
                drawHeight.toFloat() - PADDING,
                paint
            )

            // 将水印叠加到 YUV 数据（Y + UV 平面都修改）
            applyWatermarkToYuv(yuvData, bgLeft, bgTop, drawWidth, drawHeight, bitmap)
        }

        return yuvData
    }

    /**
     * 将水印 Bitmap 应用到 YUV 数据的 Y 和 UV 平面
     *
     * NV12 内存布局：Y0 Y1 ... Yn | U0 V0 U1 V1 ... Un Vn
     * - Y 平面：[0, ySize) 逐像素替换
     * - UV 平面：[ySize, ySize + ySize/2) 每 2 字节一组 (U, V)
     *
     * @param yuvData NV12 格式的 YUV 数据
     * @param bgLeft 水印区域左边界（视频帧坐标）
     * @param bgTop 水印区域上边界（视频帧坐标）
     * @param areaWidth 水印区域宽度
     * @param areaHeight 水印区域高度
     * @param bitmap 水印位图
     */
    private fun applyWatermarkToYuv(
        yuvData: ByteArray,
        bgLeft: Int,
        bgTop: Int,
        areaWidth: Int,
        areaHeight: Int,
        bitmap: Bitmap
    ) {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        val ySize = width * height

        // 获取 Bitmap 像素数据
        val watermarkPixels = IntArray(bitmapWidth * bitmapHeight)
        bitmap.getPixels(watermarkPixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)

        // 叠加水印到 Y 平面和 UV 平面
        for (y in bgTop until (bgTop + areaHeight)) {
            if (y !in 0..<height) continue
            for (x in bgLeft until (bgLeft + areaWidth)) {
                if (x !in 0..<width) continue

                val wx = x - bgLeft
                val wy = y - bgTop

                if (wx < bitmapWidth && wy < bitmapHeight) {
                    val pixelIndex = wy * bitmapWidth + wx
                    if (pixelIndex < watermarkPixels.size) {
                        val pixel = watermarkPixels[pixelIndex]
                        val alpha = (pixel shr 24) and 0xFF

                        if (alpha > 128) { // 只处理不透明区域
                            // ARGB → 提取 R/G/B 转为 YUV
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF

                            // BT.601 YUV 转换
                            val watermarkY = ((66 * r + 129 * g + 25 * b + 128) shr 8).coerceIn(0, 255)
                            val watermarkU = ((-38 * r - 74 * g + 112 * b + 128) shr 8 + 128).coerceIn(0, 255)
                            val watermarkV = ((112 * r - 94 * g - 18 * b + 128) shr 8 + 128).coerceIn(0, 255)

                            // ---- Y 平面 ----
                            val yIndex = y * width + x
                            val originalY = yuvData[yIndex].toInt() and 0xFF
                            val blendedY = (watermarkY * alpha + originalY * (255 - alpha)) / 255
                            yuvData[yIndex] = blendedY.toByte()

                            // ---- UV 平面 (NV12: U V 交织) ----
                            // UV 区域是 2x2 下采样，只在偶数行偶数列时写入
                            if (y % 2 == 0 && x % 2 == 0) {
                                val uvIndex = ySize + (y / 2) * width + x  // NV12: UV 紧跟 Y 平面
                                if (uvIndex + 1 < yuvData.size) {
                                    // U 分量
                                    val originalU = yuvData[uvIndex].toInt() and 0xFF
                                    val blendedU = (watermarkU * alpha + originalU * (255 - alpha)) / 255
                                    yuvData[uvIndex] = blendedU.toByte()

                                    // V 分量
                                    val originalV = yuvData[uvIndex + 1].toInt() and 0xFF
                                    val blendedV = (watermarkV * alpha + originalV * (255 - alpha)) / 255
                                    yuvData[uvIndex + 1] = blendedV.toByte()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 生成带时间戳的水印文本
     *
     * @param timestamp 时间戳（毫秒）
     * @return 格式化的时间文本
     */
    fun formatTimestamp(timestamp: Long): String {
        return DATE_FORMAT.format(timestamp)
    }

    /**
     * 释放水印资源
     */
    fun release() {
        synchronized(lock) {
            watermarkBitmap?.recycle()
            watermarkBitmap = null
            watermarkCanvas = null
            watermarkPaint = null
        }
        Log.i(TAG, "水印资源已释放")
    }
}
