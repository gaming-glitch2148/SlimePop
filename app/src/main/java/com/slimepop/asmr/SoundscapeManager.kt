package com.slimepop.asmr

import android.content.Context
import android.media.MediaPlayer

class SoundscapeManager(private val ctx: Context) {
    private var mp: MediaPlayer? = null

    fun playLoop(resId: Int) {
        if (resId == 0) return
        stop()
        try {
            mp = MediaPlayer.create(ctx, resId)?.apply {
                isLooping = true
                setVolume(0.35f, 0.35f)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mp = null
        }
    }

    fun stop() {
        try {
            mp?.stop()
            mp?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mp = null
    }
}
