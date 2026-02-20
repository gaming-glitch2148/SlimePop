package com.slimepop.asmr

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

class SoundscapeManager(private val ctx: Context) {
    private var mp: MediaPlayer? = null
    private var soundPool: SoundPool? = null
    private var popSoundId: Int = -1

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(attrs)
            .build()
        
        // Load the pop sound
        val resId = ctx.resources.getIdentifier("pop", "raw", ctx.packageName)
        if (resId != 0) {
            popSoundId = soundPool?.load(ctx, resId, 1) ?: -1
        }
    }

    fun playLoop(resId: Int) {
        if (resId == 0) return
        stopLoop()
        try {
            mp = MediaPlayer.create(ctx, resId)?.apply {
                isLooping = true
                setVolume(0.4f, 0.4f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mp = null
        }
    }

    fun playPop() {
        if (popSoundId != -1) {
            soundPool?.play(popSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun stopLoop() {
        try {
            mp?.stop()
            mp?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mp = null
    }

    fun release() {
        stopLoop()
        soundPool?.release()
        soundPool = null
    }
}
