package com.slimepop.asmr

import android.content.Context

object SoundLibrary {
    fun soundpackResId(ctx: Context, soundId: String): Int {
        val n = soundId.removePrefix("sound_").toIntOrNull()?.coerceIn(1, 50) ?: 1
        val name = "soundpack_%03d".format(n)
        return ctx.resources.getIdentifier(name, "raw", ctx.packageName)
    }
}