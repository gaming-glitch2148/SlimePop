package com.slimepop.asmr

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.*

class SlimeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onPop: ((coinsEarned: Int, holdMs: Long) -> Unit)? = null
    var isRelaxMode: Boolean = false
        set(value) { field = value; invalidate() }
    var hapticsEnabled: Boolean = true
        set(value) { field = value; isHapticFeedbackEnabled = value }

    private val paint        = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ripplePaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 8f }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint     = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val speckPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // Slime is oval: this many times taller than wide
    private val slimeAspectY = 1.75f

    private enum class HapticStyle { POP, SPLIT, FRENZY }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private var cx = 0f
    private var cy = 0f
    private var baseRadius = 0f

    private var pressed = false
    private var pressStartMs = 0L
    private var charge = 0f
    private var touchX = 0f; private var touchY = 0f
    private var lastTouchX = 0f; private var lastTouchY = 0f
    private var activeTouch = false
    private var wobbleX = 0f; private var wobbleY = 0f
    private var wobbleVx = 0f; private var wobbleVy = 0f
    private var pokeImpulse = 0f

    private var popStreak = 0
    private var lastPopTime = 0L
    private var isFrenzy = false
    private var frenzyEndMs = 0L

    private data class Bubble(
        val relX: Float, val relY: Float, val relR: Float,
        var popped: Boolean = false,
        var growth: Float = 0f,
        val rotation: Float = 0f,
        val isGolden: Boolean = false,
        var clusterSize: Int = 1   // 1 = solo, 2 = merged pair (needs two pops, gives bonus)
    )
    private val slimeBubbles = mutableListOf<Bubble>()
    private val maxBubbles = 15

    private data class Ripple(var x: Float, var y: Float, var r: Float, var a: Int)
    private val ripples = ArrayList<Ripple>()

    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, var color: Int = Color.WHITE)
    private val particles = ArrayList<Particle>()

    private var currentSkin: SlimeSkin = SkinCatalog.getSkinById("skin_ocean")
    private val random = Random()
    private val slimePath = Path()
    private var lastSpawnTime = 0L

    private var bodyGradient: RadialGradient? = null
    private val bgColor = Color.parseColor("#0B0F14")
    private val glossyOvalRect = RectF()

    fun setSkin(skinId: String) {
        val newSkin = SkinCatalog.skins.find { it.id == skinId } ?: SkinCatalog.getSkinById("skin_ocean")
        if (currentSkin.id != newSkin.id) {
            currentSkin = newSkin
            bodyGradient = null
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) {
            cx = w / 2f
            cy = h * 0.50f                     // vertically centered (was 0.56)
            baseRadius = min(w, h) * 0.46f     // slightly larger base
            bodyGradient = null
            wobbleX = 0f; wobbleY = 0f; wobbleVx = 0f; wobbleVy = 0f
            generateInitialBubbles()
            invalidate()
        }
    }

    private fun generateInitialBubbles() {
        slimeBubbles.clear()
        for (i in 0 until 8) spawnNewBubble(true)
    }

    private fun spawnNewBubble(instant: Boolean = false) {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val dist = random.nextFloat() * 0.72f
        val isGolden = !isRelaxMode && random.nextFloat() < 0.05f
        slimeBubbles.add(Bubble(
            relX = cos(angle) * dist,
            relY = sin(angle) * dist,
            relR = 0.08f + random.nextFloat() * 0.12f,
            growth = if (instant) 1f else 0f,
            rotation = random.nextFloat() * 360f,
            isGolden = isGolden
        ))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgColor)
        updateAnimations()

        val skin = currentSkin
        val time = SystemClock.elapsedRealtime() / 1000f
        val R = if (baseRadius > 0) baseRadius else min(width, height) * 0.46f
        if (R <= 0) return
        val dcx = if (cx > 0) cx else width / 2f
        val dcy = if (cy > 0) cy else height / 2f

        createIrregularPath(dcx, dcy, R, time)

        // Frenzy outer glow
        if (isFrenzy) {
            paint.shader = null
            paint.color = Color.YELLOW
            paint.alpha = (30 + 20 * sin(time * 10f)).toInt().coerceIn(0, 255)
            canvas.save(); canvas.scale(1.15f, 1.15f, dcx, dcy)
            canvas.drawPath(slimePath, paint); canvas.restore()
        }

        // Body radial gradient
        if (bodyGradient == null) {
            val bright = SkinCatalog.lighten(skin.highlightColor, if (skin.isNeon) 0.35f else 0.18f)
            val deep = mixColor(skin.baseColor, Color.BLACK, if (skin.isNeon) 0.30f else 0.20f)
            bodyGradient = RadialGradient(
                dcx - R * 0.18f,
                dcy - R * slimeAspectY * 0.26f,
                R * slimeAspectY * 0.90f,
                intArrayOf(bright, skin.highlightColor, skin.baseColor, deep),
                floatArrayOf(0f, 0.34f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = bodyGradient; paint.alpha = 255
        canvas.drawPath(slimePath, paint); paint.shader = null

        // Subsurface scatter – inner gel luminosity
        val hc = skin.highlightColor
        paint.shader = RadialGradient(
            dcx + R * 0.06f, dcy + R * slimeAspectY * 0.18f,
            R * slimeAspectY * 0.62f,
            Color.argb(if (skin.isNeon) 52 else 36, Color.red(hc), Color.green(hc), Color.blue(hc)),
            Color.argb(0, Color.red(hc), Color.green(hc), Color.blue(hc)),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(slimePath, paint); paint.shader = null

        // Neon outer pulse
        if (skin.isNeon) {
            paint.color = skin.highlightColor
            paint.alpha = (40 + 25 * sin(time * 3f)).toInt().coerceIn(0, 255)
            canvas.save(); canvas.scale(1.05f, 1.05f, dcx, dcy)
            canvas.drawPath(slimePath, paint); canvas.restore()
        }

        // Bottom shadow
        paint.shader = LinearGradient(
            dcx, dcy - R * slimeAspectY * 0.4f, dcx, dcy + R * slimeAspectY,
            Color.argb(0, 0, 0, 0), Color.argb(72, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawPath(slimePath, paint); paint.shader = null

        // Internal texture + gloss (clipped)
        canvas.save()
        canvas.clipPath(slimePath)
        drawDeepSlimeTexture(canvas, skin, dcx, dcy, R, time)

        val glossX = cos(time * 0.8f) * R * 0.04f + wobbleX * 0.15f
        val glossY = sin(time * 0.65f) * R * 0.03f + wobbleY * 0.1f
        glossyOvalRect.set(
            dcx - R * 0.55f + glossX, dcy - R * slimeAspectY * 0.68f + glossY,
            dcx + R * 0.15f + glossX, dcy - R * slimeAspectY * 0.10f + glossY
        )
        highlightPaint.shader = RadialGradient(
            glossyOvalRect.centerX(), glossyOvalRect.centerY(), glossyOvalRect.width(),
            Color.argb(190, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP
        )
        canvas.drawOval(glossyOvalRect, highlightPaint)
        highlightPaint.shader = null
        canvas.restore()

        // Secondary sharp specular (top-right)
        canvas.save(); canvas.clipPath(slimePath)
        val s2x = dcx + R * 0.34f + glossX * 0.5f
        val s2y = dcy - R * slimeAspectY * 0.28f + glossY * 0.5f
        val s2r = R * 0.082f
        highlightPaint.shader = RadialGradient(s2x, s2y, s2r,
            Color.argb(142, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawCircle(s2x, s2y, s2r, highlightPaint)
        highlightPaint.shader = null; canvas.restore()

        // Double rim
        rimPaint.strokeWidth = (R * 0.015f).coerceAtLeast(2.5f)
        rimPaint.color = SkinCatalog.lighten(skin.highlightColor, 0.30f)
        rimPaint.alpha = if (skin.isNeon) 170 else 110
        canvas.drawPath(slimePath, rimPaint)
        rimPaint.strokeWidth = (R * 0.034f).coerceAtLeast(5f)
        rimPaint.color = mixColor(skin.baseColor, Color.WHITE, if (skin.isNeon) 0.44f else 0.24f)
        rimPaint.alpha = if (skin.isNeon) 105 else 72
        canvas.drawPath(slimePath, rimPaint)

        // ── Bubbles ──────────────────────────────────────────
        val bubbleBase = mixColor(skin.baseColor, skin.highlightColor, 0.58f)
        val bubbleSpec = SkinCatalog.lighten(skin.highlightColor, if (skin.isNeon) 0.55f else 0.38f)

        for (b in slimeBubbles) {
            if (b.popped || b.growth <= 0f) continue
            val bx = dcx + b.relX * R
            val by = dcy + b.relY * R * slimeAspectY
            val baseR = b.relR * R * b.growth

            if (b.clusterSize > 1) {
                // Merged pair – draw as two overlapping chambers
                val cr = baseR * 1.28f
                val off = cr * 0.44f
                val sides = floatArrayOf(-1f, 1f)
                for (s in sides) {
                    val cx2 = bx + s * off
                    // haze
                    bubblePaint.color = if (b.isGolden) Color.argb(40, 255, 200, 0)
                        else Color.argb(18, Color.red(bubbleBase), Color.green(bubbleBase), Color.blue(bubbleBase))
                    canvas.drawCircle(cx2, by, cr * 0.94f, bubblePaint)
                    // fill
                    bubblePaint.color = if (b.isGolden) Color.YELLOW else bubbleBase
                    bubblePaint.alpha = (if (b.isGolden) 160 else (30 + 75 * b.growth).toInt()).coerceIn(0, 255)
                    canvas.drawCircle(cx2, by, cr * 0.82f, bubblePaint)
                    // specular
                    bubblePaint.color = if (b.isGolden) Color.WHITE else bubbleSpec
                    bubblePaint.alpha = (85 * b.growth).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx2 - cr * 0.22f, by - cr * 0.32f, cr * 0.28f, bubblePaint)
                    // rim
                    bubblePaint.style = Paint.Style.STROKE
                    bubblePaint.strokeWidth = (cr * 0.06f).coerceAtLeast(1f)
                    bubblePaint.color = SkinCatalog.lighten(bubbleSpec, 0.50f)
                    bubblePaint.alpha = (62 * b.growth).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx2, by, cr * 0.82f - bubblePaint.strokeWidth * 0.5f, bubblePaint)
                    bubblePaint.style = Paint.Style.FILL
                }
            } else {
                val br = baseR
                // Outer haze
                bubblePaint.color = if (b.isGolden) Color.argb(45, 255, 200, 0)
                    else Color.argb(20, Color.red(bubbleBase), Color.green(bubbleBase), Color.blue(bubbleBase))
                canvas.drawCircle(bx, by, br * 1.14f, bubblePaint)
                // Fill
                bubblePaint.color = if (b.isGolden) Color.YELLOW else bubbleBase
                bubblePaint.alpha = (if (b.isGolden) 150 + (60 * b.growth).toInt() else (25 + 90 * b.growth).toInt()).coerceIn(0, 255)
                canvas.drawCircle(bx, by, br, bubblePaint)
                // Primary specular
                bubblePaint.color = if (b.isGolden) Color.WHITE else bubbleSpec
                bubblePaint.alpha = (90 * b.growth).toInt().coerceIn(0, 255)
                canvas.drawCircle(bx - br * 0.25f, by - br * 0.35f, br * 0.33f, bubblePaint)
                // Secondary specular
                bubblePaint.color = Color.WHITE
                bubblePaint.alpha = (116 * b.growth).toInt().coerceIn(0, 255)
                canvas.drawCircle(bx + br * 0.24f, by + br * 0.22f, br * 0.12f, bubblePaint)
                // Rim stroke
                bubblePaint.style = Paint.Style.STROKE
                bubblePaint.strokeWidth = (br * 0.06f).coerceAtLeast(1f)
                bubblePaint.color = if (b.isGolden) Color.WHITE else SkinCatalog.lighten(bubbleSpec, 0.44f)
                bubblePaint.alpha = (66 * b.growth).toInt().coerceIn(0, 255)
                canvas.drawCircle(bx, by, br - bubblePaint.strokeWidth * 0.5f, bubblePaint)
                bubblePaint.style = Paint.Style.FILL
            }
        }

        // Particles & ripples
        for (p in particles) { paint.color = p.color; paint.alpha = (p.life * 10).coerceIn(0, 255); canvas.drawCircle(p.x, p.y, 6f, paint) }
        for (rp in ripples) { ripplePaint.color = skin.highlightColor; ripplePaint.alpha = rp.a; canvas.drawCircle(rp.x, rp.y, rp.r, ripplePaint) }

        // Relax label drawn inside the slime (FRENZY already shown via coin chip x2 tag)
        if (isRelaxMode) {
            paint.shader = null; paint.color = Color.WHITE; paint.alpha = 130
            paint.textSize = 38f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText("RELAX", dcx, dcy - R * slimeAspectY * 0.52f, paint)
        }

        postInvalidateOnAnimation()
    }

    private fun createIrregularPath(centerX: Float, centerY: Float, radius: Float, time: Float) {
        slimePath.reset()
        val segments = 48
        val angleStep = (2 * PI / segments).toFloat()
        val points = ArrayList<PointF>(segments)
        val wobbleStrength = (hypot(wobbleX, wobbleY) / (radius * 0.35f)).coerceIn(0f, 1.5f)
        val wobbleAngle = atan2(wobbleY, wobbleX)

        for (i in 0 until segments) {
            val angle = i * angleStep
            val wave = 0.028f * sin(time * 2.2f + i * 0.9f) + 0.014f * cos(time * 3.3f + i * 1.7f)
            val stretch = if (wobbleStrength > 0f) 0.08f * cos(angle - wobbleAngle) * wobbleStrength else 0f
            val touch = if (activeTouch || pressed) {
                val nx = ((touchX - centerX) / radius).coerceIn(-1f, 1f)
                val ny = ((touchY - centerY) / (radius * slimeAspectY)).coerceIn(-1f, 1f)
                0.06f * (cos(angle) * nx + sin(angle) * ny) * (0.4f + charge)
            } else 0f
            val r = radius * (1f + wave + stretch + touch + 0.09f * charge + 0.04f * pokeImpulse)
            points.add(PointF(
                centerX + cos(angle) * r + wobbleX * 0.1f,
                centerY + sin(angle) * r * slimeAspectY + wobbleY * 0.1f * slimeAspectY
            ))
        }

        val firstMid = midpoint(points[0], points[1])
        slimePath.moveTo(firstMid.x, firstMid.y)
        for (i in points.indices) {
            val cur = points[(i + 1) % points.size]
            val nxt = points[(i + 2) % points.size]
            val mid = midpoint(cur, nxt)
            slimePath.quadTo(cur.x, cur.y, mid.x, mid.y)
        }
        slimePath.close()
    }

    private fun drawDeepSlimeTexture(canvas: Canvas, skin: SlimeSkin, cx: Float, cy: Float, radius: Float, time: Float) {
        val yR = radius * slimeAspectY
        val seed = abs(skin.id.hashCode())
        val brightVein = mixColor(skin.highlightColor, Color.WHITE, if (skin.isNeon) 0.48f else 0.30f)
        val deepVein = mixColor(skin.baseColor, Color.BLACK, if (skin.isNeon) 0.38f else 0.27f)
        val shimmerColors = shimmerPaletteFor(skin)
        val drift = time * if (isRelaxMode) 0.18f else 0.28f

        texturePaint.shader = null
        texturePaint.strokeWidth = (radius * 0.012f).coerceAtLeast(2f)
        for (i in 0 until 9) {
            val lane = -0.62f + i * 0.155f
            texturePaint.color = if (i % 2 == 0) brightVein else deepVein
            texturePaint.alpha = if (i % 2 == 0) 38 else 24
            val path = Path()
            path.moveTo(cx - radius * 0.78f, cy + yR * (lane + 0.035f * sin(drift + i * 1.7f)))
            for (step in 1..5) {
                val t = step / 5f
                val wave = sin(drift * 1.6f + seed * 0.001f + i * 0.9f + t * 5.4f)
                path.lineTo(cx - radius * 0.78f + radius * 1.56f * t + wobbleX * 0.035f,
                            cy + yR * (lane + wave * 0.05f) + wobbleY * 0.035f)
            }
            canvas.drawPath(path, texturePaint)
        }

        texturePaint.strokeWidth = (radius * 0.006f).coerceAtLeast(1.2f)
        texturePaint.color = mixColor(skin.highlightColor, Color.WHITE, 0.55f)
        texturePaint.alpha = if (skin.isNeon) 86 else 54
        for (i in 0 until 7) {
            val a = (seed * 0.013f + i * 0.92f + drift * 0.35f) % (2f * PI.toFloat())
            val bandR = radius * (0.22f + i * 0.075f)
            canvas.drawArc(cx - bandR + wobbleX * 0.04f, cy - bandR * 0.62f * slimeAspectY + wobbleY * 0.04f,
                           cx + bandR + wobbleX * 0.04f, cy + bandR * 0.62f * slimeAspectY + wobbleY * 0.04f,
                           a * 57.2958f, 38f, false, texturePaint)
        }

        val speckBase = if (skin.isNeon) Color.WHITE else SkinCatalog.lighten(skin.highlightColor, 0.42f)
        for (i in 0 until 42) {
            val ls = seed + i * 110351
            val angle = ((ls % 6283) / 1000f) + drift * (0.08f + (i % 5) * 0.01f)
            val dist = radius * (0.12f + ((ls / 7) % 760) / 1000f)
            speckPaint.color = if (i % 9 == 0) Color.WHITE else speckBase
            speckPaint.alpha = if (i % 9 == 0) 78 else 34
            canvas.drawCircle(
                cx + cos(angle) * dist + sin(drift + i) * radius * 0.012f,
                cy + sin(angle * 0.91f) * dist * slimeAspectY * 0.82f + cos(drift * 0.8f + i) * yR * 0.01f,
                (radius * (0.0045f + ((ls / 31) % 22) / 10000f)).coerceAtLeast(1.1f), speckPaint
            )
        }

        texturePaint.style = Paint.Style.STROKE; texturePaint.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 18) {
            val ls = seed * 31 + i * 7919
            val angle = ((ls % 6283) / 1000f) + drift * 0.12f
            val dist = radius * (0.18f + ((ls / 17) % 650) / 1000f)
            val sx = cx + cos(angle) * dist
            val sy = cy + sin(angle * 1.07f) * dist * slimeAspectY * 0.78f
            val sz = radius * (0.018f + ((ls / 53) % 28) / 1000f)
            val twinkle = (0.5f + 0.5f * sin(time * 1.9f + i * 0.73f)).coerceIn(0f, 1f)
            texturePaint.color = shimmerColors[i % shimmerColors.size]
            texturePaint.alpha = (42 + twinkle * if (skin.isNeon) 92 else 68).toInt().coerceIn(0, 170)
            texturePaint.strokeWidth = (radius * 0.006f).coerceAtLeast(1.4f)
            canvas.drawLine(sx - sz, sy, sx + sz, sy, texturePaint)
            canvas.drawLine(sx, sy - sz * 0.62f, sx, sy + sz * 0.62f, texturePaint)
            speckPaint.color = Color.WHITE; speckPaint.alpha = (28 + twinkle * 58).toInt().coerceIn(0, 110)
            canvas.drawCircle(sx - sz * 0.22f, sy - sz * 0.18f, (radius * 0.006f).coerceAtLeast(1.3f), speckPaint)
        }

        paint.shader = RadialGradient(
            cx + radius * 0.22f + wobbleX * 0.04f, cy + yR * 0.34f + wobbleY * 0.04f, yR * 0.85f,
            Color.argb(0, 0, 0, 0), Color.argb(if (skin.isNeon) 44 else 58, 0, 0, 0), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx + radius * 0.14f, cy + yR * 0.20f, yR * 0.88f, paint)
        paint.shader = null
    }

    private fun shimmerPaletteFor(skin: SlimeSkin): IntArray {
        val warm  = mixColor(skin.highlightColor, Color.rgb(255, 211, 125), 0.38f)
        val cool  = mixColor(skin.baseColor,      Color.rgb(100, 238, 255), 0.46f)
        val rose  = mixColor(skin.highlightColor, Color.rgb(255, 128, 214), 0.34f)
        val mint  = mixColor(skin.baseColor,      Color.rgb(155, 255, 205), 0.40f)
        val pearl = SkinCatalog.lighten(skin.highlightColor, if (skin.isNeon) 0.62f else 0.46f)
        return intArrayOf(pearl, warm, cool, rose, mint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true; pressStartMs = SystemClock.elapsedRealtime()
                touchX = event.x; touchY = event.y
                lastTouchX = event.x; lastTouchY = event.y
                activeTouch = true; return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x; touchY = event.y; activeTouch = true
                val dx = event.x - lastTouchX; val dy = event.y - lastTouchY
                wobbleVx += dx * 0.11f; wobbleVy += dy * 0.11f
                pokeImpulse = (pokeImpulse + hypot(dx, dy) / 450f).coerceIn(0f, 1f)
                lastTouchX = event.x; lastTouchY = event.y; return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) {
                    val heldMs = SystemClock.elapsedRealtime() - pressStartMs
                    val radius = if (baseRadius > 0) baseRadius else min(width, height) * 0.46f
                    val bubble = findNearestBubble(event.x, event.y, radius)
                    if (bubble != null) {
                        if (bubble.clusterSize > 1) {
                            bubble.clusterSize = 1           // split one chamber off
                            handleSplitLogic(event.x, event.y, bubble, heldMs)
                        } else {
                            bubble.popped = true
                            handlePopLogic(event.x, event.y, bubble, heldMs)
                        }
                    }
                }
                activeTouch = false; pressed = false; charge = 0f; pokeImpulse *= 0.8f; return true
            }
            MotionEvent.ACTION_CANCEL -> { activeTouch = false; pressed = false; charge = 0f; return true }
        }
        return true
    }

    private fun findNearestBubble(tx: Float, ty: Float, r: Float): Bubble? {
        val bcx = if (cx > 0) cx else width / 2f
        val bcy = if (cy > 0) cy else height / 2f
        for (b in slimeBubbles) {
            if (b.popped || b.growth < 0.5f) continue
            val bx = bcx + b.relX * r
            val by = bcy + b.relY * r * slimeAspectY
            val dist = sqrt((tx - bx).pow(2) + (ty - by).pow(2))
            if (dist < b.relR * r * (if (b.clusterSize > 1) 3.4f else 2.5f)) return b
        }
        return null
    }

    private fun handlePopLogic(x: Float, y: Float, bubble: Bubble, heldMs: Long) {
        val now = SystemClock.elapsedRealtime()
        val justTriggeredFrenzy = !isFrenzy && popStreak >= 4 && (now - lastPopTime < 1500)
        if (now - lastPopTime < 1500) { popStreak++; if (popStreak >= 5) { isFrenzy = true; frenzyEndMs = now + 10000 } }
        else popStreak = 1
        lastPopTime = now
        haptic(if (justTriggeredFrenzy) HapticStyle.FRENZY else HapticStyle.POP)
        addParticles(x, y, if (bubble.isGolden) Color.YELLOW else Color.WHITE)
        ripples.add(Ripple(x, y, 20f, 200))
        wobbleVx += (x - cx) * 0.02f; wobbleVy += (y - cy) * 0.02f
        pokeImpulse = pokeImpulse.coerceAtLeast(0.45f)
        if (isRelaxMode) onPop?.invoke(0, heldMs)
        else { var coins = if (bubble.isGolden) 25 else 1; if (isFrenzy) coins *= 2; onPop?.invoke(coins, heldMs) }
        slimeBubbles.removeAll { it.popped }
    }

    private fun handleSplitLogic(x: Float, y: Float, bubble: Bubble, heldMs: Long) {
        haptic(HapticStyle.SPLIT)
        addParticles(x, y, if (bubble.isGolden) Color.argb(200, 255, 200, 50) else Color.WHITE)
        ripples.add(Ripple(x, y, 14f, 155))
        wobbleVx += (x - cx) * 0.012f; wobbleVy += (y - cy) * 0.012f
        pokeImpulse = pokeImpulse.coerceAtLeast(0.30f)
        if (!isRelaxMode) {
            val base = if (bubble.isGolden) 12 else 2
            onPop?.invoke(if (isFrenzy) base * 2 else base, heldMs)
        } else onPop?.invoke(0, heldMs)
    }

    private fun haptic(style: HapticStyle = HapticStyle.POP) {
        if (!hapticsEnabled || !Prefs.getHaptics(context)) return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // API 30+: rich haptic composition
                    val comp = VibrationEffect.startComposition()
                    when (style) {
                        HapticStyle.POP -> comp
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, 0.9f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f, 8)
                        HapticStyle.SPLIT -> comp
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f)
                        HapticStyle.FRENZY -> comp
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.7f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 70)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f, 50)
                    }
                    vibrator.vibrate(comp.compose())
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    // API 26–29: VibrationEffect with duration + amplitude
                    val effect = when (style) {
                        HapticStyle.POP    -> VibrationEffect.createOneShot(22, 220)
                        HapticStyle.SPLIT  -> VibrationEffect.createOneShot(14, 150)
                        HapticStyle.FRENZY -> VibrationEffect.createWaveform(
                            longArrayOf(0, 25, 12, 22), intArrayOf(0, 230, 0, 190), -1
                        )
                    }
                    vibrator.vibrate(effect)
                }
                else -> {
                    // Pre-API 26 fallback
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(when (style) { HapticStyle.FRENZY -> 30L; else -> 20L })
                }
            }
        } catch (_: Exception) { /* vibrator unavailable on this device */ }
    }

    private fun addParticles(x: Float, y: Float, color: Int) {
        for (i in 0 until 15) {
            val ang = random.nextFloat() * 2 * PI.toFloat()
            val spd = 2f + 5f * random.nextFloat()
            particles.add(Particle(x, y, cos(ang) * spd, sin(ang) * spd, 20 + random.nextInt(10), color))
        }
    }

    private fun updateAnimations() {
        val now = SystemClock.elapsedRealtime()
        if (now > frenzyEndMs) isFrenzy = false
        if (pressed) charge = ((now - pressStartMs) / 1000f).coerceIn(0f, 1f)

        val curR = if (baseRadius > 0) baseRadius else min(width, height) * 0.46f
        if (curR > 0f) {
            val targetX = if (activeTouch) ((touchX - cx).coerceIn(-curR, curR)) * 0.20f else 0f
            val targetY = if (activeTouch) ((touchY - cy).coerceIn(-curR * slimeAspectY, curR * slimeAspectY)) * 0.20f else 0f
            wobbleVx += (targetX - wobbleX) * 0.06f; wobbleVy += (targetY - wobbleY) * 0.06f
            wobbleVx += -wobbleX * 0.025f; wobbleVy += -wobbleY * 0.025f
            wobbleVx *= 0.87f; wobbleVy *= 0.87f
            wobbleX += wobbleVx; wobbleY += wobbleVy
        }

        if (!pressed) pokeImpulse *= 0.94f
        for (b in slimeBubbles) if (!b.popped && b.growth < 1f) b.growth += 0.02f

        // Proximity merge: two fully-grown solo bubbles that overlap → cluster
        if (!isFrenzy && curR > 0f) {
            outer@ for (i in slimeBubbles.indices) {
                val bi = slimeBubbles[i]
                if (bi.popped || bi.growth < 0.90f || bi.clusterSize > 1) continue
                for (j in (i + 1) until slimeBubbles.size) {
                    val bj = slimeBubbles[j]
                    if (bj.popped || bj.growth < 0.90f || bj.clusterSize > 1) continue
                    val dx = (bi.relX - bj.relX) * curR
                    val dy = (bi.relY - bj.relY) * curR * slimeAspectY
                    if (sqrt(dx * dx + dy * dy) < (bi.relR + bj.relR) * curR * 0.88f) {
                        bi.clusterSize = 2; bj.popped = true; break@outer
                    }
                }
            }
        }
        slimeBubbles.removeAll { it.popped }   // clean up merged/popped

        if (slimeBubbles.size < maxBubbles && now - lastSpawnTime > (if (isFrenzy) 400 else 1000)) {
            spawnNewBubble(); lastSpawnTime = now
        }

        val rit = ripples.iterator()
        while (rit.hasNext()) { val rp = rit.next(); rp.r += 9f + pokeImpulse * 2f; rp.a -= 10; if (rp.a <= 0) rit.remove() }
        val pit = particles.iterator()
        while (pit.hasNext()) { val p = pit.next(); p.vx *= 0.98f; p.vy = p.vy * 0.98f + 0.12f; p.x += p.vx; p.y += p.vy; p.life--; if (p.life <= 0) pit.remove() }
    }

    private fun midpoint(a: PointF, b: PointF) = PointF((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val r = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(from)   + (Color.red(to)   - Color.red(from))   * r).toInt().coerceIn(0, 255),
            (Color.green(from) + (Color.green(to) - Color.green(from)) * r).toInt().coerceIn(0, 255),
            (Color.blue(from)  + (Color.blue(to)  - Color.blue(from))  * r).toInt().coerceIn(0, 255)
        )
    }
}
