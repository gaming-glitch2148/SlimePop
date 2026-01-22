package com.slimepop.asmr

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class SlimeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    data class SkinVisual(val baseColor: Int, val highlightColor: Int)

    private fun skinVisualFor(index0: Int): SkinVisual {
        val i = index0.coerceIn(0, 49)
        val hue = (i * 360f / 50f)
        val base = Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.95f))
        val hi = Color.HSVToColor(floatArrayOf(hue, 0.25f, 1.0f))
        return SkinVisual(base, hi)
    }

    var onPop: ((coinsEarned: Int, holdMs: Long) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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

    private data class Ripple(var x: Float, var y: Float, var r: Float, var a: Int)
    private val ripples = ArrayList<Ripple>()

    private data class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Int)
    private val particles = ArrayList<Particle>()

    private var skinIndex: Int = 0 

    fun setSkinIndex(index0: Int) {
        skinIndex = index0.coerceIn(0, 49)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f
        cy = h / 2f
        baseRadius = min(w, h) * 0.22f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#0B0F14"))

        if (baseRadius <= 0) return // Safety check to prevent RadialGradient crash

        updateAnimations()

        val vis = skinVisualFor(skinIndex)

        val squishX = 1f + 0.10f * charge
        val squishY = 1f - 0.18f * charge

        val r = baseRadius * (1f + 0.35f * charge)
        val rx = r * squishX
        val ry = r * squishY

        // shadow
        paint.color = Color.BLACK
        paint.alpha = 100
        canvas.drawOval(cx - rx, cy + ry * 0.75f, cx + rx, cy + ry * 1.25f, paint)

        // body gradient
        val shader = RadialGradient(
            cx - rx * 0.2f,
            cy - ry * 0.2f,
            (r * 1.25f).coerceAtLeast(1f), // Extra safety for radius
            vis.highlightColor,
            vis.baseColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = shader
        paint.alpha = 255
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        paint.shader = null

        // glossy highlight
        paint.color = Color.WHITE
        paint.alpha = (35 + 90 * (1f - charge)).toInt()
        canvas.drawOval(cx - rx * 0.55f, cy - ry * 0.65f, cx - rx * 0.05f, cy - ry * 0.10f, paint)

        // particles
        paint.color = vis.highlightColor
        for (p in particles) {
            paint.alpha = (p.life * 6).coerceIn(0, 180)
            canvas.drawCircle(p.x, p.y, 6f, paint)
        }

        // ripples
        for (rp in ripples) {
            ripplePaint.color = vis.highlightColor
            ripplePaint.alpha = rp.a
            canvas.drawCircle(rp.x, rp.y, rp.r, ripplePaint)
        }

        // instruction
        paint.color = Color.WHITE
        paint.alpha = 140
        paint.textSize = 42f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (pressed) "Release to Pop" else "Press & Hold", cx, height * 0.18f, paint)

        if (pressed || ripples.isNotEmpty() || particles.isNotEmpty()) postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                pressStartMs = SystemClock.elapsedRealtime()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - cx) * 0.06f
                val dy = (event.y - cy) * 0.06f
                cx = (cx + dx).coerceIn(width * 0.25f, width * 0.75f)
                cy = (cy + dy).coerceIn(height * 0.25f, height * 0.75f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pressed) {
                    val heldMs = (SystemClock.elapsedRealtime() - pressStartMs).coerceAtLeast(0L)
                    val c = (heldMs / 900f).coerceIn(0f, 1f)

                    addRipple(event.x, event.y, c)
                    addParticles(event.x, event.y, c)

                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                    val coins = (1 + (4 * c)).roundToInt()
                    onPop?.invoke(coins, heldMs)
                }
                pressed = false
                charge = 0f
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun addRipple(x: Float, y: Float, c: Float) {
        val startR = baseRadius * (0.4f + 0.2f * c)
        ripples.add(Ripple(x, y, startR, 200))
    }

    private fun addParticles(x: Float, y: Float, c: Float) {
        val count = (18 + 30 * c).roundToInt()
        for (i in 0 until count) {
            val ang = (i.toFloat() / count) * (2f * Math.PI.toFloat())
            val spd = 3f + 10f * c
            val vx = cos(ang) * spd + (Math.random().toFloat() - 0.5f) * 1.2f
            val vy = sin(ang) * spd + (Math.random().toFloat() - 0.5f) * 1.2f
            particles.add(Particle(x, y, vx, vy, 30))
        }
    }

    private fun updateAnimations() {
        if (pressed) {
            val heldMs = (SystemClock.elapsedRealtime() - pressStartMs).coerceAtLeast(0L)
            charge = (heldMs / 900f).coerceIn(0f, 1f)
        }

        // ripples
        val rit = ripples.iterator()
        while (rit.hasNext()) {
            val rp = rit.next()
            rp.r += 18f
            rp.a -= 9
            if (rp.a <= 0) rit.remove()
        }

        // particles
        val pit = particles.iterator()
        while (pit.hasNext()) {
            val p = pit.next()
            p.x += p.vx
            p.y += p.vy
            p.vx *= 0.97f
            p.vy *= 0.97f
            p.life -= 1
            if (p.life <= 0) pit.remove()
        }
    }
}
