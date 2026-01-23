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

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private var cx = 0f
    private var cy = 0f
    private var baseRadius = 0f

    private var pressed = false
    private var pressStartMs = 0L
    private var charge = 0f // 0..1

    private data class Bubble(
        val relX: Float, 
        val relY: Float, 
        val relR: Float, 
        var popped: Boolean = false, 
        var growth: Float = 0f,
        val rotation: Float = 0f
    )
    private val slimeBubbles = mutableListOf<Bubble>()
    private val maxBubbles = 15

    private data class Ripple(var x: Float, var y: Float, var r: Float, var a: Int)
    private val ripples = ArrayList<Ripple>()

    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int, var color: Int = Color.WHITE)
    private val particles = ArrayList<Particle>()

    private var skinIndex: Int = 0 
    private val random = Random()
    private val slimePath = Path()
    private var lastSpawnTime = 0L

    private var bodyGradient: RadialGradient? = null
    private val bgColor = Color.parseColor("#0B0F14")
    private val glossyOvalRect = RectF()

    fun setSkinIndex(index0: Int) {
        val newIdx = index0.coerceIn(0, 49)
        if (skinIndex != newIdx) {
            skinIndex = newIdx
            bodyGradient = null // Reset cached gradient
            invalidate()
        }
    }

    fun setSkin(skinId: String) {
        val index = skinId.removePrefix("skin_").toIntOrNull()?.minus(1) ?: 0
        setSkinIndex(index)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w > 0 && h > 0) {
            cx = w / 2f
            cy = h / 2f
            baseRadius = min(w, h) * 0.45f
            bodyGradient = null 
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
        slimeBubbles.add(Bubble(bx, by, br, growth = if (instant) 1f else 0f, rotation = random.nextFloat() * 360f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgColor)

        updateAnimations()

        val colors = SkinColors.get(skinIndex)
        val time = SystemClock.elapsedRealtime() / 1000f

        val effectiveRadius = if (baseRadius > 0) baseRadius else min(width, height) * 0.45f
        if (effectiveRadius <= 0) return 

        val drawCx = if (cx > 0) cx else width / 2f
        val drawCy = if (cy > 0) cy else height / 2f

        createIrregularPath(drawCx, drawCy, effectiveRadius, time)

        // Draw shadow
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = 80
        canvas.save()
        canvas.translate(0f, effectiveRadius * 0.1f)
        canvas.scale(1.05f, 1.02f, drawCx, drawCy)
        canvas.drawPath(slimePath, paint)
        canvas.restore()

        // Body gradient
        if (bodyGradient == null) {
            bodyGradient = RadialGradient(
                drawCx - effectiveRadius * 0.2f,
                drawCy - effectiveRadius * 0.2f,
                effectiveRadius * 1.5f,
                colors.hi,
                colors.base,
                Shader.TileMode.CLAMP
            )
        }
        paint.shader = bodyGradient
        paint.alpha = 255
        canvas.drawPath(slimePath, paint)
        paint.shader = null

        // Draw bubbles inside slime
        for (b in slimeBubbles) {
            if (b.popped || b.growth <= 0f) continue
            
            val bx = drawCx + b.relX * effectiveRadius * (1f + 0.1f * sin(time + b.relX))
            val by = drawCy + b.relY * effectiveRadius * (1f + 0.1f * cos(time + b.relY))
            val br = b.relR * effectiveRadius * b.growth
            
            bubblePaint.color = Color.WHITE
            bubblePaint.alpha = (40 * b.growth).toInt()
            canvas.drawCircle(bx, by, br, bubblePaint)
            
            bubblePaint.alpha = (80 * b.growth).toInt()
            canvas.drawCircle(bx - br * 0.3f, by - br * 0.3f, br * 0.3f, bubblePaint)
        }

        // Glossy highlight
        paint.color = Color.WHITE
        paint.alpha = (35 + 40 * (1f - charge)).toInt()
        glossyOvalRect.set(
            drawCx - effectiveRadius * 0.6f, 
            drawCy - effectiveRadius * 0.7f, 
            drawCx - effectiveRadius * 0.1f, 
            drawCy - effectiveRadius * 0.2f
        )
        canvas.drawOval(glossyOvalRect, paint)

        // Particles
        for (p in particles) {
            paint.color = p.color
            paint.alpha = (p.life * 8).coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, 6f, paint)
        }

        // Ripples
        for (rp in ripples) {
            ripplePaint.color = colors.hi
            ripplePaint.alpha = rp.a
            canvas.drawCircle(rp.x, rp.y, rp.r, ripplePaint)
        }

        // Instruction
        paint.color = Color.WHITE
        paint.alpha = 140
        paint.textSize = 42f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (pressed) "Release to Pop" else "Press & Hold", drawCx, height * 0.12f, paint)

        postInvalidateOnAnimation()
    }

    private fun createIrregularPath(centerX: Float, centerY: Float, radius: Float, time: Float) {
        slimePath.reset()
        val segments = 12
        val angleStep = (2 * PI / segments).toFloat()
        
        val squishX = 1f + 0.05f * charge
        val squishY = 1f - 0.10f * charge

        for (i in 0 until segments) {
            val angle = i * angleStep
            val wave = 0.05f * sin(time * 2f + i * 1.5f) + 0.03f * cos(time * 1.3f + i * 0.8f)
            val r = radius * (1f + wave) * (1f + 0.2f * charge)
            
            val x = centerX + cos(angle) * r * squishX
            val y = centerY + sin(angle) * r * squishY
            
            if (i == 0) {
                slimePath.moveTo(x, y)
            } else {
                val prevAngle = (i - 1) * angleStep
                val cp1x = centerX + cos(prevAngle + angleStep * 0.5f) * radius * squishX * 1.1f
                val cp1y = centerY + sin(prevAngle + angleStep * 0.5f) * radius * squishY * 1.1f
                
                slimePath.quadTo(cp1x, cp1y, x, y)
            }
        }
        slimePath.close()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentCx = if (cx > 0) cx else width / 2f
        val currentCy = if (cy > 0) cy else height / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                pressStartMs = SystemClock.elapsedRealtime()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - currentCx) * 0.04f
                val dy = (event.y - currentCy) * 0.04f
                cx = (currentCx + dx).coerceIn(width * 0.05f, width * 0.95f)
                cy = (currentCy + dy).coerceIn(height * 0.05f, height * 0.95f)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    val heldMs = (SystemClock.elapsedRealtime() - pressStartMs).coerceAtLeast(0L)
                    val c = (heldMs / 900f).coerceIn(0f, 1f)
                    val effectiveRadius = if (baseRadius > 0) baseRadius else min(width, height) * 0.45f

                    val didPop = popNearestBubble(event.x, event.y, effectiveRadius)

                    if (didPop) {
                        addRipple(event.x, event.y, c, effectiveRadius)
                        addParticles(event.x, event.y, c, Color.WHITE)

                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                        val coins = (1 + (4 * c)).roundToInt()
                        onPop?.invoke(coins, heldMs)
                    } else {
                        addRipple(event.x, event.y, 0.1f, effectiveRadius * 0.5f)
                    }
                }
                pressed = false
                charge = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun popNearestBubble(tx: Float, ty: Float, r: Float): Boolean {
        var nearest: Bubble? = null
        var minDist = Float.MAX_VALUE
        val time = SystemClock.elapsedRealtime() / 1000f

        val currentCx = if (cx > 0) cx else width / 2f
        val currentCy = if (cy > 0) cy else height / 2f

        for (b in slimeBubbles) {
            if (b.popped || b.growth < 0.5f) continue
            val bx = currentCx + b.relX * r * (1f + 0.1f * sin(time + b.relX))
            val by = currentCy + b.relY * r * (1f + 0.1f * cos(time + b.relY))
            val dist = sqrt((tx - bx).pow(2) + (ty - by).pow(2))
            
            val bubbleRadius = b.relR * r * b.growth
            if (dist < minDist && dist < bubbleRadius * 2.0f) { 
                minDist = dist
                nearest = b
            }
        }
        
        nearest?.let {
            it.popped = true
            val bx = currentCx + it.relX * r * (1f + 0.1f * sin(time + it.relX))
            val by = currentCy + it.relY * r * (1f + 0.1f * cos(time + it.relY))
            addParticles(bx, by, 0.5f, Color.WHITE, countFactor = 1.5f)
            slimeBubbles.removeAll { it.popped }
            return true
        }
        return false
    }

    private fun addRipple(x: Float, y: Float, c: Float, r: Float) {
        val startR = r * (0.2f + 0.2f * c)
        ripples.add(Ripple(x, y, startR, 200))
    }

    private fun addParticles(x: Float, y: Float, c: Float, color: Int, countFactor: Float = 1f) {
        val count = ((10 + 20 * c) * countFactor).roundToInt()
        for (i in 0 until count) {
            val ang = (i.toFloat() / count) * (2f * Math.PI.toFloat()) + (random.nextFloat() - 0.5f)
            val spd = 2f + 8f * c * random.nextFloat()
            val vx = cos(ang) * spd
            val vy = sin(ang) * spd
            particles.add(Particle(x, y, vx, vy, 20 + random.nextInt(15), color))
        }
    }

    private fun updateAnimations() {
        val now = SystemClock.elapsedRealtime()
        
        if (pressed) {
            val heldMs = (now - pressStartMs).coerceAtLeast(0L)
            charge = (heldMs / 900f).coerceIn(0f, 1f)
        }

        for (b in slimeBubbles) {
            if (!b.popped && b.growth < 1f) {
                b.growth += 0.01f
            }
        }

        if (slimeBubbles.size < maxBubbles && now - lastSpawnTime > 1500) {
            spawnNewBubble(false)
            lastSpawnTime = now
        }

        val rit = ripples.iterator()
        while (rit.hasNext()) {
            val rp = rit.next()
            rp.r += 12f
            rp.a -= 8
            if (rp.a <= 0) rit.remove()
        }

        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.95f
            p.vy *= 0.95f
            p.life -= 1
            if (p.life <= 0) pit.remove()
        }
    }
}
