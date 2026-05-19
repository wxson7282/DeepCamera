package com.example.security_camera.camera_manager

import android.media.MediaCodecInfo
import android.media.MediaCodecList

/**
 * H265/HEVC 编解码支持检测工具
 *
 * 用法：
 *   HevcSupportUtils.isHevcSupported()          // 是否支持 H265 解码
 *   HevcSupportUtils.isHevcEncoderSupported()   // 是否支持 H265 编码
 *   HevcSupportUtils.getHevcSupportInfo()        // 获取完整支持信息
 */
object HevcSupportUtils {
    private const val MIME_HEVC = "video/hevc"

    /**
     * 检测设备是否支持 H265 解码
     */
    fun isHevcSupported(): Boolean {
        return MIME_HEVC.findDecoder() != null
    }

    /**
     * 检测设备是否支持 H265 编码
     */
    fun isHevcEncoderSupported(): Boolean {
        return MIME_HEVC.findEncoder() != null
    }

    /**
     * 获取 H265 解码器信息
     */
    fun getHevcDecoderInfo(): MediaCodecInfo? = MIME_HEVC.findDecoder()

    /**
     * 获取 H265 编码器信息
     */
    fun getHevcEncoderInfo(): MediaCodecInfo? = MIME_HEVC.findEncoder()

    /**
     * 获取完整的 H265 支持信息，方便日志输出或调试
     */
    fun getHevcSupportInfo(): HevcSupportInfo {
        val decoder = MIME_HEVC.findDecoder()
        val encoder = MIME_HEVC.findEncoder()

        val decoderCapabilities = decoder?.let { info ->
            MIME_HEVC.getCodecCapabilities(info)
        }

        val encoderCapabilities = encoder?.let { info ->
            MIME_HEVC.getCodecCapabilities(info)
        }

        return HevcSupportInfo(
            decoderSupported = decoder != null,
            encoderSupported = encoder != null,
            decoderName = decoder?.name,
            encoderName = encoder?.name,
            decoderCapabilities = decoderCapabilities,
            encoderCapabilities = encoderCapabilities
        )
    }

    /**
     * 检测 H265 编码是否支持指定分辨率
     *
     * @param width  视频宽度
     * @param height 视频高度
     * @param bitrate 比特率（bps），传0则不检查
     * @param frameRate 帧率，传0则不检查
     */
    fun isHevcEncoderSupportedForSize(
        width: Int,
        height: Int,
        bitrate: Int = 0,
        frameRate: Int = 0
    ): Boolean {
        val encoder = MIME_HEVC.findEncoder() ?: return false

        return try {
            val caps = encoder.getCapabilitiesForType(MIME_HEVC)
            val videoCaps = caps.videoCapabilities ?: return false

            // 检查分辨率支持
            if (!videoCaps.isSizeSupported(width, height)) {
                return false
            }

            // 检查比特率
            if (bitrate > 0 && videoCaps.bitrateRange != null) {
                if (bitrate < videoCaps.bitrateRange.lower || bitrate > videoCaps.bitrateRange.upper) {
                    return false
                }
            }

            // 检查帧率
            if (frameRate > 0 && videoCaps.supportedFrameRates != null) {
                if (frameRate < videoCaps.supportedFrameRates.lower || frameRate > videoCaps.supportedFrameRates.upper) {
                    return false
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // ==================== 内部方法 ====================

    private fun String.findDecoder(): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.firstOrNull { codec ->
            !codec.isEncoder && codec.supportedTypes.contains(this)
        }
    }

    private fun String.findEncoder(): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.firstOrNull { codec ->
            codec.isEncoder && codec.supportedTypes.contains(this)
        }
    }

    private fun String.getCodecCapabilities(info: MediaCodecInfo): CodecCapInfo? {
        return try {
            val caps = info.getCapabilitiesForType(this)
            val videoCaps = caps.videoCapabilities ?: return null
            CodecCapInfo(
                widthRange = "${videoCaps.supportedWidths.lower}~${videoCaps.supportedWidths.upper}",
                heightRange = "${videoCaps.supportedHeights.lower}~${videoCaps.supportedHeights.upper}",
                bitrateRange = "${videoCaps.bitrateRange.lower}~${videoCaps.bitrateRange.upper}",
                frameRateRange = "${videoCaps.supportedFrameRates.lower}~${videoCaps.supportedFrameRates.upper}",
                colorFormats = caps.colorFormats.toList()
            )
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 数据类 ====================

    data class HevcSupportInfo(
        val decoderSupported: Boolean,
        val encoderSupported: Boolean,
        val decoderName: String?,
        val encoderName: String?,
        val decoderCapabilities: CodecCapInfo?,
        val encoderCapabilities: CodecCapInfo?
    ) {
        override fun toString(): String = buildString {
            appendLine("=== H265/HEVC 支持检测 ===")
            appendLine("解码支持: ${if (decoderSupported) "✅" else "❌"} ${decoderName ?: ""}")
            appendLine("编码支持: ${if (encoderSupported) "✅" else "❌"} ${encoderName ?: ""}")
            decoderCapabilities?.let {
                appendLine("--- 解码器能力 ---")
                appendLine("  分辨率: ${it.widthRange} x ${it.heightRange}")
                appendLine("  比特率: ${it.bitrateRange}")
                appendLine("  帧率: ${it.frameRateRange}")
            }
            encoderCapabilities?.let {
                appendLine("--- 编码器能力 ---")
                appendLine("  分辨率: ${it.widthRange} x ${it.heightRange}")
                appendLine("  比特率: ${it.bitrateRange}")
                appendLine("  帧率: ${it.frameRateRange}")
                appendLine("  颜色格式: ${it.colorFormats}")
            }
        }
    }

    data class CodecCapInfo(
        val widthRange: String,
        val heightRange: String,
        val bitrateRange: String,
        val frameRateRange: String,
        val colorFormats: List<Int>
    )

}