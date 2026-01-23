package com.slimepop.asmr

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

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

class SlimeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Using Compose Paint/Shader classes here, consistent with your existing implementation.
    private val paint = androidx.compose.ui.graphics.Paint(androidx.compose.ui.graphics.Paint.ANTI_ALIAS_FLAG)

    private var currentSkinId = "skin_ocean"

    // 3D articulation variables
    private var touchX = 0f
    private var touchY = 0f
    private var isTouching = false

    var onPop: ((baseCoins: Int, holdMs: Long) -> Unit)? = null
    private var touchDownTime = 0L

    fun setSkin(skinId: String) {
        currentSkinId = skinId
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchX = w / 2f
        touchY = h / 2f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchX = event.x
        touchY = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isTouching = true
                touchDownTime = System.currentTimeMillis()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> invalidate()
            MotionEvent.ACTION_UP -> {
                isTouching = false
                val holdTime = System.currentTimeMillis() - touchDownTime
                onPop?.invoke(10, holdTime)
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val skin = SkinCatalog.getSkinById(currentSkinId)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = kotlin.math.min(w, h) / 2.5f

        // --- Recommended metallic/shimmer upgrade ---
        // Add a third “spark” stop (near-white) to create a moving specular highlight.
        val spark = SkinCatalog.lighten(skin.highlightColor, 0.65f)

        val shader = androidx.compose.ui.graphics.RadialGradient(
            touchX,
            touchY,
            radius * 1.6f,
            intArrayOf(spark, skin.highlightColor, skin.baseColor),
            floatArrayOf(0.0f, 0.35f, 1.0f),
            androidx.compose.ui.graphics.Shader.TileMode.CLAMP
        )
        paint.shader = shader

        // --- Neon glow handling (shadow) ---
        // Only neon skins should glow. Metallics look better without shadow fog.
        if (skin.isNeon) {
            paint.setShadowLayer(55f, 0f, 0f, skin.highlightColor)
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
        } else {
            paint.clearShadowLayer()
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        val scale = if (isTouching) 0.92f else 1.0f
        canvas.save()
        canvas.scale(scale, scale, w / 2, h / 2)
        canvas.drawCircle(w / 2, h / 2, radius, paint)
        canvas.restore()
    }
}

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
        SlimeSkin("skin_ocean", "Tropical Ocean", c("#0077BE"), c("#82EEFD"), isNeon = false, coinPrice = 1000, isIAP = false),
        SlimeSkin("skin_bubblegum", "Bubblegum", c("#FF69B4"), c("#FFC0CB"), isNeon = false, coinPrice = 2000, isIAP = false),
        SlimeSkin("skin_mint", "Magic Mint", c("#00FA9A"), c("#F0FFF0"), isNeon = false, coinPrice = 5000, isIAP = false),
        SlimeSkin("skin_lavender", "Lavender Sky", c("#967BB6"), c("#E6E6FA"), isNeon = false, coinPrice = 10000, isIAP = false),

        // ORIGINAL PREMIUM SKINS (Your existing set, lightly tuned isNeon)
        SlimeSkin("skin_gold", "Golden Shimmer", c("#D4AF37"), c("#FFFACD"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_toxic", "Neon Toxic", c("#39FF14"), c("#CCFF00"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_galaxy", "Deep Galaxy", c("#2E0854"), c("#FF00FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_magma", "Molten Magma", c("#FF4500"), c("#FFFF00"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_onyx", "Midnight Onyx", c("#0F0F0F"), c("#434343"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_mercury", "Silver Mercury", c("#BEC2CB"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_emerald", "Emerald Fire", c("#50C878"), c("#99FFCC"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cyber", "Cyber Punk", c("#FF007F"), c("#00FFFF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_solar", "Solar Flare", c("#FF8C00"), c("#FFD700"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_aurora", "Aurora", c("#00FF7F"), c("#4B0082"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM METALLIC / SHIMMERY (NO glow shadow; sparkle comes from 3-stop shader)
        SlimeSkin("skin_rose_gold", "Rose Gold Luxe", c("#B76E79"), c("#FFE4E1"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_platinum", "Platinum Mirror", c("#E5E4E2"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_champagne", "Champagne Glow", c("#F7E7CE"), c("#FFF8E7"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_copper", "Liquid Copper", c("#B87333"), c("#FFDAB9"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_bronze", "Ancient Bronze", c("#CD7F32"), c("#FFF1C1"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_molten_silver", "Molten Silver", c("#9CA3AF"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_gold_leaf", "Gold Leaf", c("#C9A227"), c("#FFF4B0"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_midnight_chrome", "Midnight Chrome", c("#111827"), c("#E5E7EB"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM PEARL / OPAL / IRIDESCENT (usually best without glow)
        SlimeSkin("skin_iridescent", "Iridescent Pearl", c("#B8C6FF"), c("#FFF6FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_opal", "Opal Drift", c("#7FE7DC"), c("#FFF1FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_moonstone", "Moonstone Mist", c("#5F9EA0"), c("#F8FFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_pearl_blush", "Pearl Blush", c("#F4C2C2"), c("#FFFFFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_seafoam_pearl", "Seafoam Pearl", c("#2EE6A6"), c("#F3FFFB"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM GEMSTONE (high contrast highlights; usually no glow)
        SlimeSkin("skin_ruby", "Ruby Facet", c("#9B111E"), c("#FF4D6D"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_sapphire", "Sapphire Beam", c("#0F52BA"), c("#66CCFF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_amethyst", "Amethyst Shine", c("#6A0DAD"), c("#E0B0FF"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_topaz", "Topaz Flash", c("#FFB200"), c("#FFF2A6"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_emerald_glint", "Emerald Glint", c("#007F5F"), c("#B7FFD8"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM DARK GLOSS (clean specular, no glow)
        SlimeSkin("skin_obsidian", "Obsidian Gloss", c("#0B0B0F"), c("#5E5E70"), isNeon = false, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_black_ice", "Black Ice", c("#101820"), c("#98AFC7"), isNeon = false, coinPrice = 0, isIAP = true),

        // PREMIUM NEON / HOLO (glow enabled)
        SlimeSkin("skin_holo_prism", "Holo Prism", c("#7F00FF"), c("#00E5FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_aurora_glass", "Aurora Glass", c("#00C9FF"), c("#F0FFB3"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_prism_ice", "Prism Ice", c("#6EE7FF"), c("#FFFFFF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_neon_laser", "Neon Laser", c("#FF00E5"), c("#00FFF0"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_electric_lime", "Electric Lime", c("#76FF03"), c("#F6FF00"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_ultraviolet", "Ultraviolet Pulse", c("#3D0075"), c("#FF00FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_neon_sunset", "Neon Sunset", c("#FF3D00"), c("#FFEA00"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_gilded_violet", "Gilded Violet", c("#4B0082"), c("#FFD700"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cyber_chrome", "Cyber Chrome", c("#A0A3BD"), c("#00FFFF"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM COSMIC (glow enabled)
        SlimeSkin("skin_starlight", "Starlight", c("#1B1F3B"), c("#B7A7FF"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_cosmic_holo", "Cosmic Holo", c("#00B3FF"), c("#FF00C8"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_nova", "Nova Burst", c("#FF6A00"), c("#FFFFFF"), isNeon = true, coinPrice = 0, isIAP = true),

        // PREMIUM EXTRA FLASH (glow enabled)
        SlimeSkin("skin_dragon_scale", "Dragon Scale", c("#006D5B"), c("#7CFFCB"), isNeon = true, coinPrice = 0, isIAP = true),
        SlimeSkin("skin_ice_royal", "Ice Royal", c("#0047AB"), c("#E6F7FF"), isNeon = false, coinPrice = 0, isIAP = true)
    )

    fun getSkinById(id: String): SlimeSkin = skins.find { it.id == id } ?: skins[0]
}
