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
 * 3. 在 YUV420 数据上叠加水印
 * 4. Paint/Canvas 样式配置
 *
 * @param width 视频宽度
 * @param height 视频高度
 */
class WatermarkOverlay(
    private val width: Int,
    private val height: Int
) {
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

        // 预分配水印 Bitmap
        val bitmapWidth = (width * 0.4f).toInt()
        val bitmapHeight = (height * 0.08f).toInt()
        watermarkBitmap = createBitmap(bitmapWidth, bitmapHeight)
        watermarkCanvas = Canvas(watermarkBitmap!!)
    }

    /**
     * 在 YUV420 数据上叠加时间水印
     *
     * @param yuvData YUV 原始数据（YUV420 格式，Y-U-V 连续排列）
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

            // 计算背景区域（右下角）
            val bgLeft = width - watermarkBounds.width() - margin - PADDING * 2
            val bgTop = height - watermarkBounds.height() - margin - PADDING * 2
            val bgRight = width - margin
            val bgBottom = height - margin

            // 清空 Bitmap
            bitmap.eraseColor(0)

            // 绘制半透明黑色背景
            paint.color = Color.argb(ALPHA, 0, 0, 0)
            canvas.drawRect(
                bgLeft.toFloat(),
                bgTop.toFloat(),
                bgRight.toFloat(),
                bgBottom.toFloat(),
                paint
            )

            // 绘制白色文字
            paint.color = Color.argb(ALPHA, 255, 255, 255)
            canvas.drawText(
                watermarkText,
                bgLeft.toFloat() + PADDING,
                bgBottom.toFloat() - PADDING - watermarkBounds.height() / 2f,
                paint
            )

            // 将水印叠加到 YUV 数据
            applyWatermarkToYuv(yuvData, bgLeft, bgTop, bgRight, bgBottom, bitmap)
        }

        return yuvData
    }

    /**
     * 将水印 Bitmap 应用到 YUV 数据的 Y 平面
     *
     * @param yuvData YUV 数据
     * @param bgLeft 背景左边界
     * @param bgTop 背景上边界
     * @param bgRight 背景右边界
     * @param bgBottom 背景下边界
     * @param bitmap 水印位图
     */
    private fun applyWatermarkToYuv(
        yuvData: ByteArray,
        bgLeft: Int,
        bgTop: Int,
        bgRight: Int,
        bgBottom: Int,
        bitmap: Bitmap
    ) {
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        // 获取 Bitmap 像素数据
        val watermarkPixels = IntArray(bitmapWidth * bitmapHeight)
        bitmap.getPixels(watermarkPixels, 0, bitmapWidth, 0, 0, bitmapWidth, bitmapHeight)

        // 叠加水印到 Y 平面
        for (y in bgTop until bgBottom) {
            for (x in bgLeft until bgRight) {
                val wx = x - bgLeft
                val wy = y - bgTop

                if (wx < bitmapWidth && wy < bitmapHeight) {
                    val pixelIndex = wy * bitmapWidth + wx
                    if (pixelIndex < watermarkPixels.size) {
                        val pixel = watermarkPixels[pixelIndex]
                        val alpha = (pixel shr 24) and 0xFF

                        if (alpha > 128) { // 只处理不透明区域
                            val watermarkY = (pixel shr 16) and 0xFF
                            val originalY = yuvData[y * width + x].toInt() and 0xFF

                            // Alpha 混合
                            val blendedY = ((watermarkY * alpha + originalY * (255 - alpha)) / 255)
                            yuvData[y * width + x] = blendedY.toByte()
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
