package com.slimepop.asmr.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.annotation.RawRes

class SlimeAudioManager(
    context: Context,
    maxStreams: Int = 12
) {
    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(maxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loadedSoundIds = mutableMapOf<Int, Int>()      // rawResId -> soundId
    private val activeLoops = mutableMapOf<String, Int>()       // loopKey -> streamId

    var sfxVolume: Float = 1.0f
        set(value) { field = value.coerceIn(0f, 1f) }

    var ambienceVolume: Float = 0.6f
        set(value) {
            field = value.coerceIn(0f, 1f)
            // apply live to any active ambience loops
            activeLoops.forEach { (_, streamId) ->
                soundPool.setVolume(streamId, field, field)
            }
        }

    /**
     * Preload a resource. Call during loading screen / onCreate.
     */
    fun preload(@RawRes resId: Int) {
        if (loadedSoundIds.containsKey(resId)) return
        val soundId = soundPool.load(appContext, resId, 1)
        loadedSoundIds[resId] = soundId
    }

    fun preloadAll(vararg resIds: Int) {
        resIds.forEach { preload(it) }
    }

    /**
     * Play a one-shot SFX.
     */
    fun playSfx(@RawRes resId: Int, volume: Float = sfxVolume, rate: Float = 1.0f) {
        val soundId = loadedSoundIds[resId] ?: return
        soundPool.play(soundId, volume, volume, 1, 0, rate.coerceIn(0.5f, 2.0f))
    }

    /**
     * Start or replace a seamless loop.
     * loopKey lets you manage multiple loops (e.g. "ambience", "menu", "boss").
     */
    fun playLoop(loopKey: String, @RawRes resId: Int, volume: Float = ambienceVolume, rate: Float = 1.0f) {
        stopLoop(loopKey)
        val soundId = loadedSoundIds[resId] ?: return
        val streamId = soundPool.play(soundId, volume, volume, 1, -1, rate.coerceIn(0.5f, 2.0f))
        if (streamId != 0) activeLoops[loopKey] = streamId
    }

    fun stopLoop(loopKey: String) {
        val streamId = activeLoops.remove(loopKey) ?: return
        soundPool.stop(streamId)
    }

    fun stopAllLoops() {
        val keys = activeLoops.keys.toList()
        keys.forEach { stopLoop(it) }
    }

    /**
     * Call from Activity/Fragment onPause.
     */
    fun pauseAll() {
        soundPool.autoPause()
    }

    /**
     * Call from Activity/Fragment onResume.
     */
    fun resumeAll() {
        soundPool.autoResume()
    }

    fun release() {
        activeLoops.clear()
        loadedSoundIds.clear()
        soundPool.release()
    }
}
