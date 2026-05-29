package com.slimepop.asmr

import android.graphics.Color

/**
 * Season passes – each maps to ONE Play Store non-consumable SKU created once.
 * To add/rotate content for a new year's pass, update the skinIds/soundIds here
 * and ship an app update. No new SKUs ever needed.
 *
 * Suggested pricing: individual items are $0.99 each, so a 6-skin + 2-sound
 * season saves the buyer ~$5 vs buying individually.
 */
data class Season(
    val id: String,
    val name: String,
    val emoji: String,
    val tagline: String,
    val skinIds: List<String>,
    val soundIds: List<String>,
    val gradientStart: Int,
    val gradientEnd: Int,
    val priceHint: String = "$3.99"
) {
    val grantIds: List<String> get() = skinIds + soundIds

    val contentSummary: String get() {
        val s = skinIds.size
        val sn = soundIds.size
        return "$s Skin${if (s != 1) "s" else ""} · $sn Sound${if (sn != 1) "s" else ""}"
    }

    fun previewLabel(): String {
        val names = skinIds.mapNotNull { id -> SkinCatalog.skins.find { it.id == id }?.name }
        return if (names.size <= 3) names.joinToString(" · ")
        else "${names.take(3).joinToString(" · ")} +${names.size - 3} more"
    }
}

object SeasonCatalog {
    private fun c(hex: String) = Color.parseColor(hex)

    val seasons = listOf(
        Season(
            id = "season_summer",
            name = "Summer Splash",
            emoji = "☀️",
            tagline = "Tropical heat & electric energy",
            skinIds  = listOf("skin_solar", "skin_aurora_glass", "skin_electric_lime",
                               "skin_seafoam_pearl", "skin_dragon_scale", "skin_aurora"),
            soundIds = listOf("sound_004", "sound_015"),
            gradientStart = c("#FF6B35"), gradientEnd = c("#FFD460"),
            priceHint = "$3.99"
        ),
        Season(
            id = "season_gemstone",
            name = "Gemstone Collection",
            emoji = "💎",
            tagline = "Rare minerals, infinite depth",
            skinIds  = listOf("skin_ruby", "skin_sapphire", "skin_amethyst",
                               "skin_topaz", "skin_emerald_glint", "skin_opal"),
            soundIds = listOf("sound_020", "sound_007"),
            gradientStart = c("#7B2FBE"), gradientEnd = c("#23B5D3"),
            priceHint = "$2.99"
        ),
        Season(
            id = "season_fall",
            name = "Fall Harvest",
            emoji = "🍂",
            tagline = "Warm amber & rich earth tones",
            skinIds  = listOf("skin_magma", "skin_copper", "skin_bronze",
                               "skin_gold_leaf", "skin_champagne", "skin_gold"),
            soundIds = listOf("sound_006", "sound_047"),
            gradientStart = c("#E74C3C"), gradientEnd = c("#F39C12"),
            priceHint = "$3.99"
        ),
        Season(
            id = "season_winter",
            name = "Winter Frost",
            emoji = "❄️",
            tagline = "Icy mirrors & midnight chrome",
            skinIds  = listOf("skin_black_ice", "skin_platinum", "skin_iridescent",
                               "skin_moonstone", "skin_midnight_chrome", "skin_prism_ice"),
            soundIds = listOf("sound_009", "sound_034"),
            gradientStart = c("#0077B6"), gradientEnd = c("#90E0EF"),
            priceHint = "$3.99"
        ),
        Season(
            id = "season_cosmic",
            name = "Cosmic Collection",
            emoji = "🌌",
            tagline = "Deep space, supernovas & starlight",
            skinIds  = listOf("skin_galaxy", "skin_starlight", "skin_cosmic_holo",
                               "skin_nova", "skin_ultraviolet", "skin_gilded_violet"),
            soundIds = listOf("sound_025", "sound_050"),
            gradientStart = c("#0D0D2B"), gradientEnd = c("#7B2FBE"),
            priceHint = "$3.99"
        ),
        Season(
            id = "season_neon",
            name = "Neon Rush",
            emoji = "⚡",
            tagline = "High voltage, zero chill",
            skinIds  = listOf("skin_toxic", "skin_cyber", "skin_holo_prism",
                               "skin_neon_laser", "skin_cyber_chrome", "skin_neon_sunset"),
            soundIds = listOf("sound_010", "sound_012"),
            gradientStart = c("#FF00E5"), gradientEnd = c("#00FFFF"),
            priceHint = "$3.99"
        )
    )

    fun getSeasonById(id: String): Season? = seasons.find { it.id == id }

    fun grantsFor(seasonId: String): List<String> =
        getSeasonById(seasonId)?.grantIds ?: emptyList()

    /** Returns the total cost of buying every item individually at $0.99 each. */
    fun individualValue(season: Season): Float =
        (season.skinIds.size + season.soundIds.size) * 0.99f
}
