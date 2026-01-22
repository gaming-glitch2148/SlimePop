package com.slimepop.asmr

object BundleGrants {

    data class BundleDef(
        val bundleId: String,
        val grantIds: List<String>,
        val displayName: String
    )

    val bundles: List<BundleDef> = (1..20).map { i ->
        val id = "bundle_%02d".format(i)
        val grants = when {
            i % 5 == 1 -> listOf(skin(i*2-1), skin(i*2), sound(i))
            i % 5 == 2 -> listOf(skin(i), sound(i*2-1), sound(i*2))
            i % 5 == 3 -> listOf(skin(i*2-1), skin(i*2), skin(i*2+1))
            i % 5 == 4 -> listOf(sound(i*2-1), sound(i*2), sound(i*2+1))
            else       -> listOf(skin(i), skin(i+10), sound(i+10))
        }.map { clamp(it) }.distinct().padTo3Fallback(i)

        BundleDef(
            bundleId = id,
            grantIds = grants,
            displayName = "Relax Pack ${i.toString().padStart(2,'0')}"
        )
    }

    private fun skin(n: Int) = "skin_%03d".format(n)
    private fun sound(n: Int) = "sound_%03d".format(n)

    private fun clamp(id: String): String {
        return when {
            id.startsWith("skin_") -> {
                val n = id.removePrefix("skin_").toIntOrNull() ?: 1
                "skin_%03d".format(n.coerceIn(1, 50))
            }
            id.startsWith("sound_") -> {
                val n = id.removePrefix("sound_").toIntOrNull() ?: 1
                "sound_%03d".format(n.coerceIn(1, 50))
            }
            else -> id
        }
    }

    private fun List<String>.padTo3Fallback(seed: Int): List<String> {
        if (size >= 3) return take(3)
        val out = toMutableList()
        var k = seed
        while (out.size < 3) {
            val candidate = if (k % 2 == 0) "skin_%03d".format((k % 50) + 1) else "sound_%03d".format((k % 50) + 1)
            if (!out.contains(candidate)) out.add(candidate)
            k++
        }
        return out
    }

    fun displayNameFor(bundleId: String): String =
        bundles.firstOrNull { it.bundleId == bundleId }?.displayName ?: bundleId

    fun grantsFor(bundleId: String): List<String> =
        bundles.firstOrNull { it.bundleId == bundleId }?.grantIds ?: emptyList()

    fun bundleDescription(bundleId: String): String {
        val grants = grantsFor(bundleId)
        fun label(id: String) = when {
            id.startsWith("skin_") -> "Skin: ${ContentNames.skinNameFor(id)}"
            id.startsWith("sound_") -> "Sound: ${ContentNames.soundNameFor(id)}"
            else -> id
        }
        return grants.joinToString("\n") { "• ${label(it)}" }
    }
}