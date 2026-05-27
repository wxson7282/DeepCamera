package com.example.security_camera.streaming

import android.media.MediaFormat

/**
 * 编码数据监听器
 *
 * 由 VideoEncoder 在编码输出时回调，供流媒体模块消费编码后的数据。
 * 与 MediaMuxer 并行，一份数据两路输出。
 */
interface OnEncodedDataListener {

    /**
     * 编码器输出格式变化（携带 SPS/PPS 等配置信息）
     *
     * @param format MediaCodec 输出的 MediaFormat，包含 csd-0/csd-1
     */
    fun onFormatChanged(format: MediaFormat)

    /**
     * 编码数据输出
     *
     * @param data NAL 单元数据（MediaCodec 原始输出，length-prefixed 格式）
     * @param flags MediaCodec.BufferInfo.flags（BUFFER_FLAG_KEY_FRAME / BUFFER_FLAG_CODEC_CONFIG 等）
     * @param presentationTimeUs 展示时间戳（微秒）
     */
    fun onEncodedData(data: ByteArray, flags: Int, presentationTimeUs: Long)
}
