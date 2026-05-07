package com.example.security_camera.camera_manager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 * 叠加策略（两步法）：
 * - Step 1: 直接在 YUV 数据上压暗水印区域（半透明暗色背景效果）
 * - Step 2: 将文字 Bitmap 叠加到已压暗的 YUV 数据上
 * 这样背景和文字的透明度分别独立控制，互不干扰
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
        const val TEXT_SIZE_RATIO = 0.018f      // 文字大小占视频高度的比例（缩小）
        const val MARGIN_RATIO = 0.015f         // 边距占视频宽度的比例（缩小）
        const val PADDING = 6                   // 水印内边距（缩小）
        const val BG_DARKEN_RATIO = 0.25f       // 背景压暗比例（0=全透, 1=全黑）

        // 时间格式
        var DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    // 水印 Bitmap（仅用于文字渲染）
    private var watermarkBitmap: Bitmap? = null

    // 水印 Canvas（用于绘制）
    private var watermarkCanvas: Canvas? = null

    // 水印 Paint（文字绘制）
    private var watermarkPaint: Paint? = null

    // 水印区域固定位置（基于模板文字算出，防止跳动）
    private var fixedWatermarkWidth = 0
    private var fixedWatermarkHeight = 0
    private var fixedBgLeft = 0
    private var fixedBgTop = 0

    // 水印锁
    private val lock = Any()

    init {
        initResources()
    }

    /**
     * 初始化水印资源
     */
    private fun initResources() {
        // 创建水印 Paint（关闭抗锯齿和阴影，确保边缘硬切清晰）
        watermarkPaint = Paint().apply {
            color = Color.WHITE
            textSize = (height * TEXT_SIZE_RATIO).coerceAtLeast(16f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = false     // 关闭抗锯齿，避免边缘半透明像素
            clearShadowLayer()      // 关闭阴影，避免模糊扩散
        }

        // 用模板文字测量，预分配足够大的 Bitmap
        val templateText = "0000-00-00 00:00:00"
        val textBounds = android.graphics.Rect()
        watermarkPaint?.getTextBounds(templateText, 0, templateText.length, textBounds)

        // Bitmap 仅用于文字，宽高按模板文字来
        val bitmapWidth = (textBounds.width() + PADDING * 4).coerceAtMost(width)
        val bitmapHeight = (textBounds.height() + PADDING * 4).coerceAtMost(height)

        // 释放旧的 Bitmap
        watermarkBitmap?.recycle()
        watermarkBitmap = createBitmap(bitmapWidth, bitmapHeight)
        watermarkCanvas = Canvas(watermarkBitmap!!)

        // 用模板文字算出固定的水印区域位置
        val margin = (width * MARGIN_RATIO).toInt()
        fixedWatermarkWidth = textBounds.width() + PADDING * 2
        fixedWatermarkHeight = textBounds.height() + PADDING * 2
        fixedBgLeft = width - fixedWatermarkWidth - margin
        fixedBgTop = height - fixedWatermarkHeight - margin

        Log.i(TAG, "水印资源初始化: videoSize=${width}x${height}, bitmapSize=${bitmapWidth}x${bitmapHeight}, fixedPos=(${fixedBgLeft},${fixedBgTop})")
    }

    /**
     * 更新视频尺寸（实际分辨率可能与配置不同时调用）
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
     * 在 YUV420SP 数据上叠加时间水印（两步法）
     *
     * Step 1: 直接在 YUV 上压暗水印区域 → 半透明暗色背景
     * Step 2: 将文字 Bitmap 叠加到 YUV → 白色文字
     *
     * @param yuvData YUV 原始数据（NV12 格式：Y + UV 交织排列）
     * @param watermarkText 水印文本
     * @return 带水印的 YUV 数据
     */
    fun overlayWatermark(yuvData: ByteArray, watermarkText: String): ByteArray {
        val paint = watermarkPaint ?: return yuvData
        val bitmap = watermarkBitmap ?: return yuvData
        val canvas = watermarkCanvas ?: return yuvData

        val textSize = (height * TEXT_SIZE_RATIO).coerceAtLeast(24f)

        synchronized(lock) {
            paint.textSize = textSize

            val drawWidth = fixedWatermarkWidth.coerceAtMost(bitmap.width)
            val drawHeight = fixedWatermarkHeight.coerceAtMost(bitmap.height)

            // ★ Step 1: 直接在 YUV 上压暗背景区域
            applyBackgroundToYuv(yuvData, fixedBgLeft, fixedBgTop, fixedWatermarkWidth, fixedWatermarkHeight)

            // ★ Step 2: 在 Bitmap 上只画文字（不画背景）
            bitmap.eraseColor(0)
            paint.color = Color.WHITE  // 全不透明白色
            canvas.drawText(
                watermarkText,
                PADDING.toFloat(),
                drawHeight.toFloat() - PADDING,
                paint
            )

            // ★ Step 3: 将文字叠加到已压暗的 YUV 数据
            applyTextToYuv(yuvData, fixedBgLeft, fixedBgTop, drawWidth, drawHeight, bitmap)
        }

        return yuvData
    }

    /**
     * Step 1: 在 YUV 数据上直接压暗水印区域（半透明暗色背景效果）
     *
     * 原理：将原始 Y 值按比例缩小（变暗），UV 推向 128（去色）
     * BG_DARKEN_RATIO 控制压暗程度：0=不变, 1=全黑
     *
     * @param yuvData NV12 格式 YUV 数据
     * @param bgLeft 区域左边界
     * @param bgTop 区域上边界
     * @param areaWidth 区域宽度
     * @param areaHeight 区域高度
     */
    private fun applyBackgroundToYuv(
        yuvData: ByteArray,
        bgLeft: Int,
        bgTop: Int,
        areaWidth: Int,
        areaHeight: Int
    ) {
        val ySize = width * height
        val darken = BG_DARKEN_RATIO
        val retain = 1.0f - darken

        for (y in bgTop until (bgTop + areaHeight)) {
            if (y !in 0..<height) continue
            for (x in bgLeft until (bgLeft + areaWidth)) {
                if (x !in 0..<width) continue

                // Y 平面 - 压暗
                val yIndex = y * width + x
                val originalY = yuvData[yIndex].toInt() and 0xFF
                val blendedY = (originalY * retain).toInt().coerceIn(0, 255)
                yuvData[yIndex] = blendedY.toByte()

                // UV 平面 - 推向中性色（128,128）
                if (y % 2 == 0 && x % 2 == 0) {
                    val uvIndex = ySize + (y / 2) * width + x
                    if (uvIndex + 1 < yuvData.size) {
                        val originalU = yuvData[uvIndex].toInt() and 0xFF
                        val blendedU = (originalU * retain + 128 * darken).toInt().coerceIn(0, 255)
                        yuvData[uvIndex] = blendedU.toByte()

                        val originalV = yuvData[uvIndex + 1].toInt() and 0xFF
                        val blendedV = (originalV * retain + 128 * darken).toInt().coerceIn(0, 255)
                        yuvData[uvIndex + 1] = blendedV.toByte()
                    }
                }
            }
        }
    }

    /**
     * Step 2: 将文字 Bitmap 叠加到 YUV 数据（硬切边缘，无渐变）
     *
     * 硬阈值：alpha > 128 的像素直接写白色，alpha <= 128 的跳过
     * 这样文字边缘没有半透明过渡，清晰锐利
     *
     * @param yuvData NV12 格式 YUV 数据（已压暗背景）
     * @param bgLeft 区域左边界
     * @param bgTop 区域上边界
     * @param areaWidth 区域宽度
     * @param areaHeight 区域高度
     * @param bitmap 文字位图
     */
    private fun applyTextToYuv(
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

        // 白色的 YUV 值（BT.601）
        val whiteY = 235
        val whiteU = 128
        val whiteV = 128

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

                        // ★ 硬阈值：alpha > 128 直接写白色，否则跳过
                        if (alpha > 128) {
                            // ---- Y 平面 ----
                            val yIndex = y * width + x
                            yuvData[yIndex] = whiteY.toByte()

                            // ---- UV 平面 (NV12) ----
                            if (y % 2 == 0 && x % 2 == 0) {
                                val uvIndex = ySize + (y / 2) * width + x
                                if (uvIndex + 1 < yuvData.size) {
                                    yuvData[uvIndex] = whiteU.toByte()
                                    yuvData[uvIndex + 1] = whiteV.toByte()
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
