package com.example.dual_camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.media.AudioAttributesCompat

class ShutterSound(context: Context) {
    private var soundPool: SoundPool? = null
    private var soundId: Int = 0
    init {
        val audioAttributesCompat = AudioAttributesCompat.Builder()
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributesCompat.USAGE_ASSISTANCE_SONIFICATION)
            .build()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(audioAttributesCompat.contentType)
            .setUsage(audioAttributesCompat.usage)
            .build()
        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(1)
            .build()
        soundId = soundPool?.load(context, R.raw.shutter_sound, 1) ?: 0
    }

    fun play() {
        soundPool?.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
    }
    fun release() {
        soundPool?.release()
        soundPool = null
    }
}