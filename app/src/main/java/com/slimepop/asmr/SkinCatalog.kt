package com.slimepop.asmr

import android.graphics.Color

/**
 * Data model for Slime Skins.
 *
 * Notes:
 * - isNeon=true is reserved for skins that should “glow” (shadow layer).
 * - Metallic / pearl / gemstone skins typically look best with isNeon=false.
 * - isIAP=true means $0.99 purchase (managed product / non-consumable).
 */
data class SlimeSkin(
    val id: String,
    val name: String,
    val baseColor: Int,
    val highlightColor: Int,
    val isNeon: Boolean = false,
    val coinPrice: Int = 0,     // 0 means it's not a gameplay unlock
    val isIAP: Boolean = false  // If true, requires $0.99 purchase
)

object SkinCatalog {
    private fun c(hex: String) = Color.parseColor(hex)

    /**
     * Lighten a color by interpolating toward white.
     * factor: 0.0 -> unchanged, 1.0 -> pure white
     */
    fun lighten(color: Int, factor: Float): Int {
        val f = factor.coerceIn(0f, 1f)
        fun lerp(a: Int, b: Int) = (a + (b - a) * f).toInt().coerceIn(0, 255)
        return Color.rgb(
            lerp(Color.red(color), 255),
            lerp(Color.green(color), 255),
            lerp(Color.blue(color), 255)
        )
    }

    // -------- SKIN LIST --------
    // Conventions:
    // - BASIC: coin unlocks / free
    // - PREMIUM: isIAP=true, priced at $0.99 in Play Console
    // - isNeon=true only for strong glow styles (laser/toxic/holo/neon).

    val skins = listOf(
        // BASIC SKINS (Unlocked by Gameplay/Coins)
        SlimeSkin("skin_ocean", "Tropical Ocean", c("#006BA6"), c("#7AF7FF"), isNeon = false, coinPrice = 600, isIAP = false),
        SlimeSkin("skin_bubblegum", "Bubblegum", c("#F84FA7"), c("#FFD2EE"), isNeon = false, coinPrice = 1800, isIAP = false),
        SlimeSkin("skin_mint", "Magic Mint", c("#18DFA4"), c("#D9FFF4"), isNeon = false, coinPrice = 4200, isIAP = false),
        SlimeSkin("skin_lavender", "Lavender Sky", c("#8667C8"), c("#F0E7FF"), isNeon = false, coinPrice = 9000, isIAP = false),

        // ORIGINAL PREMIUM SKINS (Your existing set, lightly tuned isNeon)
        SlimeSkin("skin_gold", "Golden Shimmer", c("#C69A24"), c("#FFF1A8"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_toxic", "Neon Toxic", c("#20E83A"), c("#E8FF38"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_galaxy", "Deep Galaxy", c("#23105E"), c("#FF4CFF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_magma", "Molten Magma", c("#E93618"), c("#FFE14D"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_onyx", "Midnight Onyx", c("#08090D"), c("#687083"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_mercury", "Silver Mercury", c("#AEB7C6"), c("#F9FDFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_emerald", "Emerald Fire", c("#0AA872"), c("#B8FFD8"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cyber", "Cyber Punk", c("#F40083"), c("#45FFF4"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_solar", "Solar Flare", c("#F27A15"), c("#FFE46B"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_aurora", "Aurora", c("#00D992"), c("#7D5CFF"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM METALLIC / SHIMMERY (NO glow shadow; sparkle comes from 3-stop shader)
        SlimeSkin("skin_rose_gold", "Rose Gold Luxe", c("#A85D68"), c("#FFE2DF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_platinum", "Platinum Mirror", c("#CDD4DC"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_champagne", "Champagne Glow", c("#E8CFA6"), c("#FFF8DE"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_copper", "Liquid Copper", c("#A85E2D"), c("#FFC48E"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_bronze", "Ancient Bronze", c("#B76F2B"), c("#FFE5A1"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_molten_silver", "Molten Silver", c("#8F9AA8"), c("#F7FBFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_gold_leaf", "Gold Leaf", c("#B88D16"), c("#FFF0A4"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_midnight_chrome", "Midnight Chrome", c("#0B1220"), c("#DDEBFF"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM PEARL / OPAL / IRIDESCENT (usually best without glow)
        SlimeSkin("skin_iridescent", "Iridescent Pearl", c("#A9BFFF"), c("#FFF4FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_opal", "Opal Drift", c("#63DACF"), c("#FFF0FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_moonstone", "Moonstone Mist", c("#4D97A1"), c("#F5FFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_pearl_blush", "Pearl Blush", c("#ECB0BC"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_seafoam_pearl", "Seafoam Pearl", c("#24CFA2"), c("#F0FFF9"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM GEMSTONE (high contrast highlights; usually no glow)
        SlimeSkin("skin_ruby", "Ruby Facet", c("#8D1020"), c("#FF6A86"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_sapphire", "Sapphire Beam", c("#1048A8"), c("#74D6FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_amethyst", "Amethyst Shine", c("#6416A6"), c("#E7BEFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_topaz", "Topaz Flash", c("#E89B12"), c("#FFF0A0"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_emerald_glint", "Emerald Glint", c("#007558"), c("#B2FFD5"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM DARK GLOSS (clean specular, no glow)
        SlimeSkin("skin_obsidian", "Obsidian Gloss", c("#07080D"), c("#777B91"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_black_ice", "Black Ice", c("#0C1520"), c("#9FC4E6"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM NEON / HOLO (glow enabled)
        SlimeSkin("skin_holo_prism", "Holo Prism", c("#7A21FF"), c("#20F3FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_aurora_glass", "Aurora Glass", c("#03BDE8"), c("#F4FF9E"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_prism_ice", "Prism Ice", c("#55DFFF"), c("#FFFFFF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_neon_laser", "Neon Laser", c("#F019E6"), c("#31FFF1"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_electric_lime", "Electric Lime", c("#63EF17"), c("#F6FF31"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_ultraviolet", "Ultraviolet Pulse", c("#38008A"), c("#FF4DFF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_neon_sunset", "Neon Sunset", c("#F04B19"), c("#FFEE36"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_gilded_violet", "Gilded Violet", c("#4A168F"), c("#FFD956"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cyber_chrome", "Cyber Chrome", c("#8F96B8"), c("#34FFF8"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM COSMIC (glow enabled)
        SlimeSkin("skin_starlight", "Starlight", c("#171D43"), c("#C5B6FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cosmic_holo", "Cosmic Holo", c("#00A7F5"), c("#FF3AD2"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_nova", "Nova Burst", c("#F05F13"), c("#FFF8E8"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM EXTRA FLASH (glow enabled)
        SlimeSkin("skin_dragon_scale", "Dragon Scale", c("#006450"), c("#80FFD2"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_ice_royal", "Ice Royal", c("#0A49A8"), c("#E2F8FF"), isNeon = false, coinPrice = 0, isIAP = true)
    )

    fun getSkinById(id: String): SlimeSkin = skins.find { it.id == id } ?: skins[0]
}
