package com.slimepop.asmr

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.*

class SlimeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var onPop: ((coinsEarned: Int, holdMs: Long) -> Unit)? = null
    var isRelaxMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var cx = 0f
    private var cy = 0f
    private var baseRadius = 0f

    private var pressed = false
    private var pressStartMs = 0L
    private var charge = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activeTouch = false
    private var wobbleX = 0f
    private var wobbleY = 0f
    private var wobbleVx = 0f
    private var wobbleVy = 0f
    private var pokeImpulse = 0f

    // Competitiveness features
    private var popStreak = 0
    private var lastPopTime = 0L
    private var isFrenzy = false
    private var frenzyEndMs = 0L

    private data class Bubble(
        val relX: Float,
        val relY: Float,
        val relR: Float,
        var popped: Boolean = false,
        var growth: Float = 0f,
        val rotation: Float = 0f,
        val isGolden: Boolean = false
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
            cy = h / 2f
            baseRadius = min(w, h) * 0.45f
            bodyGradient = null 
            wobbleX = 0f
            wobbleY = 0f
            wobbleVx = 0f
            wobbleVy = 0f
            generateInitialBubbles()
            invalidate()
        }
    }

    private fun generateInitialBubbles() {
        slimeBubbles.clear()
        for (i in 0 until 8) {
            spawnNewBubble(true)
        }
    }

    private fun spawnNewBubble(instant: Boolean = false) {
        val angle = random.nextFloat() * 2 * PI.toFloat()
        val dist = random.nextFloat() * 0.75f
        val bx = cos(angle) * dist
        val by = sin(angle) * dist
        val br = 0.08f + random.nextFloat() * 0.12f
        val isGolden = !isRelaxMode && random.nextFloat() < 0.05f
        slimeBubbles.add(Bubble(bx, by, br, growth = if (instant) 1f else 0f, rotation = random.nextFloat() * 360f, isGolden = isGolden))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgColor)

        updateAnimations()

        val skin = currentSkin
        val time = SystemClock.elapsedRealtime() / 1000f

        val effectiveRadius = if (baseRadius > 0) baseRadius else min(width, height) * 0.45f
        if (effectiveRadius <= 0) return

        val drawCx = if (cx > 0) cx else width / 2f
        val drawCy = if (cy > 0) cy else height / 2f

        createIrregularPath(drawCx, drawCy, effectiveRadius, time)

        // Frenzy Glow
        if (isFrenzy) {
            paint.shader = null
            paint.color = Color.YELLOW
            paint.alpha = (30 + 20 * sin(time * 10f)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.scale(1.15f, 1.15f, drawCx, drawCy)
            canvas.drawPath(slimePath, paint)
            canvas.restore()
        }

        // Body
        if (bodyGradient == null) {
            val bright = SkinCatalog.lighten(skin.highlightColor, if (skin.isNeon) 0.35f else 0.18f)
            val deep = mixColor(skin.baseColor, Color.BLACK, if (skin.isNeon) 0.30f else 0.20f)
            bodyGradient = RadialGradient(
                drawCx - effectiveRadius * 0.2f,
                drawCy - effectiveRadius * 0.28f,
                effectiveRadius * 1.45f,
                intArrayOf(bright, skin.highlightColor, skin.baseColor, deep),
                floatArrayOf(0f, 0.34f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = bodyGradient
        paint.alpha = 255
        canvas.drawPath(slimePath, paint)
        paint.shader = null

        if (skin.isNeon) {
            paint.color = skin.highlightColor
            paint.alpha = (40 + 25 * sin(time * 3f)).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.scale(1.05f, 1.05f, drawCx, drawCy)
            canvas.drawPath(slimePath, paint)
            canvas.restore()
        }

        paint.shader = LinearGradient(
            drawCx, drawCy - effectiveRadius * 0.4f,
            drawCx, drawCy + effectiveRadius,
            Color.argb(0, 0, 0, 0),
            Color.argb(72, 0, 0, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(slimePath, paint)
        paint.shader = null

        canvas.save()
        canvas.clipPath(slimePath)
        val glossX = cos(time * 0.8f) * effectiveRadius * 0.04f + wobbleX * 0.15f
        val glossY = sin(time * 0.65f) * effectiveRadius * 0.03f + wobbleY * 0.1f
        glossyOvalRect.set(
            drawCx - effectiveRadius * 0.55f + glossX,
            drawCy - effectiveRadius * 0.70f + glossY,
            drawCx + effectiveRadius * 0.15f + glossX,
            drawCy - effectiveRadius * 0.08f + glossY
        )
        highlightPaint.shader = RadialGradient(
            glossyOvalRect.centerX(),
            glossyOvalRect.centerY(),
            glossyOvalRect.width(),
            Color.argb(190, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(glossyOvalRect, highlightPaint)
        highlightPaint.shader = null
        canvas.restore()

        rimPaint.strokeWidth = (effectiveRadius * 0.015f).coerceAtLeast(2.5f)
        rimPaint.color = mixColor(skin.highlightColor, Color.WHITE, 0.18f)
        rimPaint.alpha = 130
        canvas.drawPath(slimePath, rimPaint)

        // Bubbles
        for (b in slimeBubbles) {
            if (b.popped || b.growth <= 0f) continue
            val bx = drawCx + b.relX * effectiveRadius
            val by = drawCy + b.relY * effectiveRadius
            val br = b.relR * effectiveRadius * b.growth

            bubblePaint.color = if (b.isGolden) Color.YELLOW else Color.WHITE
            bubblePaint.alpha = (if (b.isGolden) 150 + (60 * b.growth).toInt() else (25 + 90 * b.growth).toInt()).coerceIn(0, 255)
            canvas.drawCircle(bx, by, br, bubblePaint)

            bubblePaint.color = Color.WHITE
            bubblePaint.alpha = (90 * b.growth).toInt().coerceIn(0, 255)
            canvas.drawCircle(bx - br * 0.25f, by - br * 0.35f, br * 0.33f, bubblePaint)
        }

        // Particles & Ripples
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.life * 10).coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, 6f, paint)
        }
        for (rp in ripples) {
            ripplePaint.color = skin.highlightColor
            ripplePaint.alpha = rp.a
            canvas.drawCircle(rp.x, rp.y, rp.r, ripplePaint)
        }

        // HUD Text
        paint.color = Color.WHITE
        paint.alpha = 180
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        if (isRelaxMode) {
            canvas.drawText("RELAX MODE", drawCx, height * 0.12f, paint)
        } else if (isFrenzy) {
            paint.color = Color.YELLOW
            canvas.drawText("FRENZY! 2X COINS", drawCx, height * 0.12f, paint)
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
            val directionalStretch = if (wobbleStrength > 0f) 0.08f * cos(angle - wobbleAngle) * wobbleStrength else 0f

            val touchInfluence = if (activeTouch || pressed) {
                val nx = ((touchX - centerX) / radius).coerceIn(-1f, 1f)
                val ny = ((touchY - centerY) / radius).coerceIn(-1f, 1f)
                0.06f * (cos(angle) * nx + sin(angle) * ny) * (0.4f + charge)
            } else {
                0f
            }

            val r = radius * (1f + wave + directionalStretch + touchInfluence + 0.09f * charge + 0.04f * pokeImpulse)
            val x = centerX + cos(angle) * r + wobbleX * 0.1f
            val y = centerY + sin(angle) * r + wobbleY * 0.1f
            points.add(PointF(x, y))
        }

        if (points.isNotEmpty()) {
            val firstMid = midpoint(points[0], points[1 % points.size])
            slimePath.moveTo(firstMid.x, firstMid.y)
            for (i in points.indices) {
                val current = points[(i + 1) % points.size]
                val next = points[(i + 2) % points.size]
                val mid = midpoint(current, next)
                slimePath.quadTo(current.x, current.y, mid.x, mid.y)
            }
            slimePath.close()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                pressStartMs = SystemClock.elapsedRealtime()
                touchX = event.x
                touchY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                activeTouch = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchX = event.x
                touchY = event.y
                activeTouch = true

                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                wobbleVx += dx * 0.11f
                wobbleVy += dy * 0.11f
                pokeImpulse = (pokeImpulse + hypot(dx, dy) / 450f).coerceIn(0f, 1f)

                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pressed) {
                    val heldMs = SystemClock.elapsedRealtime() - pressStartMs
                    val radius = if (baseRadius > 0) baseRadius else min(width, height) * 0.45f
                    val bubble = findNearestBubble(event.x, event.y, radius)

                    if (bubble != null) {
                        bubble.popped = true
                        handlePopLogic(event.x, event.y, bubble, heldMs)
                    }
                }
                activeTouch = false
                pressed = false
                charge = 0f
                pokeImpulse *= 0.8f
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activeTouch = false
                pressed = false
                charge = 0f
                return true
            }
        }
        return true
    }

    private fun findNearestBubble(tx: Float, ty: Float, r: Float): Bubble? {
        val currentCx = if (cx > 0) cx else width / 2f
        val currentCy = if (cy > 0) cy else height / 2f
        for (b in slimeBubbles) {
            if (b.popped || b.growth < 0.5f) continue
            val bx = currentCx + b.relX * r
            val by = currentCy + b.relY * r
            val dist = sqrt((tx - bx).pow(2) + (ty - by).pow(2))
            if (dist < b.relR * r * 2.5f) return b
        }
        return null
    }

    private fun handlePopLogic(x: Float, y: Float, bubble: Bubble, heldMs: Long) {
        val now = SystemClock.elapsedRealtime()

        // Streak Logic
        if (now - lastPopTime < 1500) {
            popStreak++
            if (popStreak >= 5) {
                isFrenzy = true
                frenzyEndMs = now + 10000
            }
        } else {
            popStreak = 1
        }
        lastPopTime = now

        // Sound & Particles
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        addParticles(x, y, if (bubble.isGolden) Color.YELLOW else Color.WHITE)
        ripples.add(Ripple(x, y, 20f, 200))
        wobbleVx += (x - cx) * 0.02f
        wobbleVy += (y - cy) * 0.02f
        pokeImpulse = pokeImpulse.coerceAtLeast(0.45f)

        // Coin Logic
        if (isRelaxMode) {
            onPop?.invoke(0, heldMs)
        } else {
            var coins = if (bubble.isGolden) 25 else 1
            if (isFrenzy) coins *= 2
            onPop?.invoke(coins, heldMs)
        }

        slimeBubbles.removeAll { it.popped }
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

        val currentCx = if (cx > 0) cx else width / 2f
        val currentCy = if (cy > 0) cy else height / 2f
        val currentR = if (baseRadius > 0) baseRadius else min(width, height) * 0.45f
        if (currentR > 0f) {
            val targetX = if (activeTouch) ((touchX - currentCx).coerceIn(-currentR, currentR)) * 0.20f else 0f
            val targetY = if (activeTouch) ((touchY - currentCy).coerceIn(-currentR, currentR)) * 0.20f else 0f

            wobbleVx += (targetX - wobbleX) * 0.06f
            wobbleVy += (targetY - wobbleY) * 0.06f
            wobbleVx += -wobbleX * 0.025f
            wobbleVy += -wobbleY * 0.025f
            wobbleVx *= 0.87f
            wobbleVy *= 0.87f
            wobbleX += wobbleVx
            wobbleY += wobbleVy
        }

        if (!pressed) pokeImpulse *= 0.94f

        for (b in slimeBubbles) if (!b.popped && b.growth < 1f) b.growth += 0.02f
        if (slimeBubbles.size < maxBubbles && now - lastSpawnTime > (if (isFrenzy) 400 else 1000)) {
            spawnNewBubble()
            lastSpawnTime = now
        }

        val rit = ripples.iterator()
        while (rit.hasNext()) {
            val rp = rit.next()
            rp.r += 9f + pokeImpulse * 2f
            rp.a -= 10
            if (rp.a <= 0) rit.remove()
        }

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.vx *= 0.98f
            p.vy = p.vy * 0.98f + 0.12f
            p.x += p.vx
            p.y += p.vy
            p.life--
            if (p.life <= 0) pit.remove()
        }
    }

    private fun midpoint(a: PointF, b: PointF): PointF = PointF((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val ratio = t.coerceIn(0f, 1f)
        val r = (Color.red(from) + (Color.red(to) - Color.red(from)) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(from) + (Color.green(to) - Color.green(from)) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
