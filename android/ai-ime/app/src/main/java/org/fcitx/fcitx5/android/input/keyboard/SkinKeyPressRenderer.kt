package org.fcitx.fcitx5.android.input.keyboard

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import org.fcitx.fcitx5.android.data.theme.SkinKeyPressEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Drawn before keyboard children. Opaque keycaps naturally occlude light and particles. */
class SkinKeyPressRenderer(private val density: Float, private val style: SkinKeyPressEffect) {
    private data class Particle(val x: Float, val y: Float, val dx: Float, val dy: Float, val size: Float, val color: Int)
    private data class Reflection(val angle: Float, val length: Float, val offset: Float, val width: Float, val intensity: Float, val phase: Float)
    private data class Burst(val start: Long, val x: Float, val y: Float, val particles: List<Particle>, val token: Any?, val reflections: List<Reflection>, val fromX: Float, val fromY: Float, var released: Long? = null)
    private val bursts = ArrayDeque<Burst>()
    private val random = Random.Default
    private var lastX = 0f
    private var lastY = 0f
    private var lastPress = Long.MIN_VALUE
    private val digits = Array(10) { it.toString() }
    private val position = SkinParticleMotion.Position()
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        textSize = this@SkinKeyPressRenderer.style.digitSizeDp * density
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val star = Path()
    private val waterPath = Path()
    private val waterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val purple = this@SkinKeyPressRenderer.style.colors.first()
        fun alpha(value: Int) = (purple and 0x00ffffff) or (value shl 24)
        shader = RadialGradient(0f, 0f, 1f,
            intArrayOf(alpha(0), alpha(65), alpha(140), alpha(105), alpha(0)),
            floatArrayOf(0f, .3f, .63f, .82f, 1f), Shader.TileMode.CLAMP)
    }
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@SkinKeyPressRenderer.style.rippleWidthDp * density
    }
    private val glowPaints = style.colors.map { color ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(0f, 0f, 1f,
                intArrayOf(color, (color and 0x00ffffff) or (Color.alpha(color) / 2 shl 24), color and 0x00ffffff),
                floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        }
    }
    val activeCount: Int get() = bursts.size

    fun clear() { bursts.clear(); lastPress = Long.MIN_VALUE }

    fun press(key: RectF, now: Long = SystemClock.uptimeMillis(), token: Any? = null): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return false
        if (key.isEmpty) return false
        expire(now)
        while (bursts.size >= style.maxBursts) bursts.removeFirst()
        val particles = List(style.particleCount) { index ->
            val angle = (2.0 * PI * (index + random.nextFloat()) / style.particleCount).toFloat()
            val distance = if (style.rippleStyle == "water") .08f + random.nextFloat() * .3f
                else if (style.rippleCount > 0) .3f + random.nextFloat() * .55f else .85f + random.nextFloat() * .35f
            val dx = cos(angle)
            val dy = sin(angle)
            Particle(dx * (key.width() / 2 + 3 * density) * distance,
                dy * (key.height() / 2 + 3 * density) * distance,
                dx * style.travelDp * density, dy * style.travelDp * density,
                style.particleSizeDp * density * (.7f + random.nextFloat() * .6f),
                style.colors[index % style.colors.size])
        }
        if (token != null) release(token, now)
        val reflections = if (style.rippleStyle == "water") List(style.fragmentCount) {
            Reflection(random.nextFloat() * 2f * PI.toFloat(),
                style.fragmentMinRadians + random.nextFloat() * (style.fragmentMaxRadians - style.fragmentMinRadians),
                (random.nextFloat() * 2f - 1f) * style.fragmentSpread,
                .28f + random.nextFloat() * .65f, .3f + random.nextFloat() * .7f,
                random.nextFloat() * 2f * PI.toFloat())
        } else emptyList()
        val follows = lastPress != Long.MIN_VALUE && now - lastPress in 0..style.followWindowMs.toLong()
        val fromX = if (follows) lastX else key.centerX()
        val fromY = if (follows) lastY else key.centerY()
        bursts.addLast(Burst(now, key.centerX(), key.centerY(), particles, token, reflections, fromX, fromY))
        lastX = key.centerX(); lastY = key.centerY(); lastPress = now
        return true
    }

    fun release(token: Any, now: Long = SystemClock.uptimeMillis()) {
        bursts.filter { it.token === token && it.released == null }.forEach { it.released = now }
    }

    fun releaseAll(now: Long = SystemClock.uptimeMillis()) {
        bursts.filter { it.released == null }.forEach { it.released = now }
    }

    private fun expire(now: Long) {
        bursts.removeAll {
            if (style.releaseDurationMs > 0 && it.token != null)
                it.released?.let { released -> now - released >= style.releaseDurationMs } ?: false
            else now - it.start >= style.durationMs
        }
    }

    /** Returns whether another animation frame is needed. No timer or redraw while idle. */
    fun draw(canvas: Canvas, now: Long = SystemClock.uptimeMillis()): Boolean {
        expire(now)
        for (burst in bursts) {
            val t = ((now - burst.start).toFloat() / style.durationMs * style.speed).coerceIn(0f, 1f)
            val releaseTail = style.releaseDurationMs > 0 && burst.token != null
            val releaseT = burst.released?.let { ((now - it).toFloat() / style.releaseDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
            // Hold the light while touched, then pause briefly before a smooth release fade.
            val fadeT = ((releaseT - .18f) / .82f).coerceIn(0f, 1f)
            val fade = if (releaseTail) 1f - fadeT * fadeT * (3f - 2f * fadeT) else (1 - t) * (1 - t)
            val radius = style.radiusDp * density * (.8f + .35f * t)
            glowPaints.forEachIndexed { index, paint ->
                val angle = 2 * PI * index / glowPaints.size
                val spread = if (glowPaints.size == 1) 0f else radius * .23f
                val save = canvas.save()
                canvas.translate(burst.x + cos(angle).toFloat() * spread, burst.y + sin(angle).toFloat() * spread)
                canvas.scale(radius, radius)
                paint.alpha = (255 * style.glowAlpha * fade).toInt()
                canvas.drawCircle(0f, 0f, 1f, paint)
                canvas.restoreToCount(save)
            }
            // Rings are below the key surfaces, so their alpha is attenuated by the
            // authored translucent cap rather than painted over the label or icon.
            if (style.rippleStyle == "water") drawWater(canvas, burst, t, fade)
            else repeat(style.rippleCount) { index ->
                val progress = ((t - index * .12f) / (1f - index * .12f)).coerceIn(0f, 1f)
                if (progress > 0f) {
                    val ringRadius = style.radiusDp * density * (.18f + .82f * progress)
                    val ringFade = fade * (1f - progress) * (progress * 8f).coerceAtMost(1f)
                    ripplePaint.color = style.colors[index % style.colors.size]
                    ripplePaint.alpha = (255 * style.rippleAlpha * ringFade * .25f).toInt()
                    ripplePaint.strokeWidth = style.rippleWidthDp * density * 4f
                    canvas.drawCircle(burst.x, burst.y, ringRadius, ripplePaint)
                    ripplePaint.alpha = (255 * style.rippleAlpha * ringFade).toInt()
                    ripplePaint.strokeWidth = style.rippleWidthDp * density
                    canvas.drawCircle(burst.x, burst.y, ringRadius, ripplePaint)
                }
            }
            for ((index, p) in burst.particles.withIndex()) {
                val water = style.rippleStyle == "water"
                val drift = if (water) sin(t * PI.toFloat() * 2f + index) * 3f * density * t else 0f
                SkinParticleMotion.sample(style, density, t, index, p.x, p.y, p.dx, p.dy,
                    burst.fromX - burst.x, burst.fromY - burst.y, position)
                val x = burst.x + position.x + if (style.motion == "expand") drift else 0f
                val y = burst.y + position.y - if (style.motion == "expand") drift * .65f else 0f
                for (segment in 1..style.trailSegments) {
                    val past = t - segment * .045f
                    if (past < 0f) break
                    SkinParticleMotion.sample(style, density, past, index, p.x, p.y, p.dx, p.dy,
                        burst.fromX - burst.x, burst.fromY - burst.y, position)
                    particlePaint.color = p.color
                    particlePaint.alpha = (Color.alpha(p.color) * fade * style.particleAlpha * .3f / segment).toInt()
                    canvas.drawCircle(burst.x + position.x, burst.y + position.y, p.size * .7f, particlePaint)
                }
                val size = p.size * (1f - .35f * t)
                particlePaint.color = p.color
                particlePaint.alpha = (Color.alpha(p.color) * fade * style.particleAlpha).toInt()
                if (water) {
                    particlePaint.alpha = (Color.alpha(p.color) * fade * style.particleAlpha * .20f).toInt()
                    canvas.drawCircle(x, y, size * 2.8f, particlePaint)
                    particlePaint.alpha = (Color.alpha(p.color) * fade * style.particleAlpha * (.75f + .25f * sin(index + t * 10f))).toInt()
                }
                if (style.particleShape == "digit") {
                    digitPaint.color = p.color
                    digitPaint.alpha = particlePaint.alpha
                    canvas.drawText(digits[index % 10], x, y - (digitPaint.ascent() + digitPaint.descent()) / 2f, digitPaint)
                } else if (style.particleShape == "dot" || (style.particleShape == "mixed" && index % 4 != 0)) canvas.drawCircle(x, y, size, particlePaint)
                else {
                    star.rewind()
                    star.moveTo(x, y - size * 2)
                    star.lineTo(x + size * .45f, y - size * .45f)
                    star.lineTo(x + size * 2, y)
                    star.lineTo(x + size * .45f, y + size * .45f)
                    star.lineTo(x, y + size * 2)
                    star.lineTo(x - size * .45f, y + size * .45f)
                    star.lineTo(x - size * 2, y)
                    star.lineTo(x - size * .45f, y - size * .45f)
                    star.close()
                    canvas.drawPath(star, particlePaint)
                }
            }
        }
        return bursts.isNotEmpty()
    }

    private fun drawWater(canvas: Canvas, burst: Burst, t: Float, fade: Float) {
        repeat(style.rippleCount) { index ->
            val progress = ((t - index * .075f) / (1f - index * .075f)).coerceIn(0f, 1f)
            if (progress <= 0f) return@repeat
            val eased = 1f - (1f-progress)*(1f-progress)
            val radius = style.radiusDp * density * (.13f + .87f * eased)
            val alpha = fade * (1f - progress * .65f) * (progress * 12f).coerceAtMost(1f)
            val save = canvas.save()
            canvas.translate(burst.x, burst.y)
            canvas.scale(radius, radius)
            waterPaint.alpha = (150 * style.rippleAlpha * alpha).toInt().coerceIn(0,255)
            canvas.drawCircle(0f, 0f, 1f, waterPaint)
            canvas.restoreToCount(save)
            // Stable random fragments move together with the wave, with dark gaps
            // between highlights. Never close the paths into a uniform bright ring.
            for (fragment in burst.reflections) {
                waterPath.rewind()
                val start = fragment.angle + index * 1.81f + progress * .055f
                for (step in 0..16) {
                    val u = step / 16f
                    val angle = start + fragment.length * u
                    val r = radius * (1f + fragment.offset +
                        style.waveDistortion * (2f / 3f) * sin(angle * 7f + fragment.phase + progress * 2f) +
                        style.waveDistortion * (1f / 3f) * sin(angle * 17f - fragment.phase))
                    val x = burst.x + cos(angle) * r
                    val y = burst.y + sin(angle) * r * .94f
                    if (step == 0) waterPath.moveTo(x, y) else waterPath.lineTo(x, y)
                }
                val shimmer = .72f + .28f * sin(fragment.phase + progress * 4f)
                reflectionPaint.color = style.colors.last()
                reflectionPaint.strokeWidth = density * style.rippleWidthDp * fragment.width * 3f
                reflectionPaint.alpha = (255 * alpha * fragment.intensity * shimmer * .13f).toInt().coerceIn(0,255)
                canvas.drawPath(waterPath, reflectionPaint)
                reflectionPaint.strokeWidth = density * style.rippleWidthDp * fragment.width
                reflectionPaint.alpha = (255 * style.rippleAlpha * alpha * fragment.intensity * shimmer).toInt().coerceIn(0,255)
                canvas.drawPath(waterPath, reflectionPaint)
            }
        }
    }
}
