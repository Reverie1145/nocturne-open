package cn.reverie.render.animation

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.draw.ShadowRenderer
import cn.reverie.render.nanovg.NanoVGColor
import kotlin.math.abs
import kotlin.math.sqrt

class MotionTracker(
    var accelFactor: Float = 0.45f,
    var decelFactor: Float = 0.10f,
    var deadzone: Float = 0.02f
) {
    var prevX: Float = Float.NaN
        private set
    var prevY: Float = Float.NaN
        private set
    var vx: Float = 0f
        private set
    var vy: Float = 0f
        private set
    var speed: Float = 0f
        private set

    val isMoving: Boolean
        get() = speed > deadzone

    fun reset(x: Float, y: Float) {
        prevX = x
        prevY = y
        vx = 0f
        vy = 0f
        speed = 0f
    }

    fun update(x: Float, y: Float): MotionTracker {
        if (prevX.isNaN() || prevY.isNaN()) {
            reset(x, y)
            return this
        }

        val rawVx = x - prevX
        val rawVy = y - prevY
        val rawSpeed = sqrt(rawVx * rawVx + rawVy * rawVy)

        val factor = if (rawSpeed >= speed) accelFactor else decelFactor
        vx += (rawVx - vx) * factor
        vy += (rawVy - vy) * factor
        speed = sqrt(vx * vx + vy * vy)

        if (speed < deadzone) {
            vx = 0f
            vy = 0f
            speed = 0f
        }

        prevX = x
        prevY = y
        return this
    }
}

object MotionBlurHelper {

    val context: Long
        get() = RenderEngine.nanoVG.handle

    fun getBlurForProgress(progress: Float, maxBlur: Float = 6.0f): Float {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 0.995f) return 0f
        val remaining = 1f - p

        val lingerFactor = Math.pow(remaining.toDouble(), 0.55).toFloat() * (1f - p * p * 0.45f)
        return (lingerFactor * maxBlur).coerceAtLeast(0f)
    }

    fun renderMotionBlur(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        vx: Float,
        vy: Float,
        color: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.28f),
        maxBlur: Float = 18f,
        samples: Int = 4
    ) {
        val speed = sqrt(vx * vx + vy * vy)
        if (speed < 0.03f) return

        val dirX = vx / speed
        val dirY = vy / speed
        val blurAmount = (speed * 1.35f).coerceIn(2.0f, maxBlur)
        val intensity = (speed / 11f).coerceIn(0.12f, 1.0f)

        val trailDistance = (speed * 0.95f).coerceAtMost(28f)
        val offsetX = -dirX * trailDistance * 0.55f
        val offsetY = -dirY * trailDistance * 0.55f

        val trailColor = color.multiplyAlpha(intensity * 0.70f)
        if (trailColor.a > 0.01f) {
            ShadowRenderer.drawShadow(
                context,
                x + offsetX,
                y + offsetY,
                width,
                height,
                radius,
                blurAmount,
                spread = 0.8f,
                color = trailColor,
                offsetY = 0f
            )
        }

        val effectiveSamples = samples.coerceIn(2, 5)
        for (i in 1..effectiveSamples) {
            val factor = i.toFloat() / (effectiveSamples + 1)
            val ghostX = x - vx * factor * 0.75f
            val ghostY = y - vy * factor * 0.75f
            val ghostAlpha = (1f - factor) * 0.24f * intensity * color.a

            if (ghostAlpha > 0.008f) {
                RoundedRect.draw(
                    context,
                    ghostX,
                    ghostY,
                    width,
                    height,
                    CornerRadii(radius),
                    color.copy(a = ghostAlpha)
                )
            }
        }
    }

    fun renderHorizontalMotionBlur(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        vx: Float,
        color: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.32f),
        maxBlur: Float = 16f
    ) {
        val speed = abs(vx)
        if (speed < 0.03f) return

        val dirX = if (vx > 0f) 1f else -1f
        val blur = (speed * 1.30f).coerceIn(2.0f, maxBlur)
        val intensity = (speed / 10f).coerceIn(0.14f, 1.0f)
        val trailDist = (speed * 0.85f).coerceAtMost(25f)

        val shadowColor = color.multiplyAlpha(intensity * 0.65f)
        ShadowRenderer.drawShadow(
            context,
            x - dirX * trailDist * 0.55f,
            y,
            width,
            height,
            radius,
            blur,
            spread = 0.5f,
            color = shadowColor,
            offsetY = 0f
        )

        for (i in 1..3) {
            val t = i * 0.30f
            val ghostX = x - vx * t * 0.65f
            val ghostAlpha = (1f - t * 0.75f) * 0.25f * intensity * color.a
            if (ghostAlpha > 0.008f) {
                RoundedRect.draw(
                    context,
                    ghostX,
                    y,
                    width,
                    height,
                    CornerRadii(radius),
                    color.copy(a = ghostAlpha)
                )
            }
        }
    }

    fun renderVerticalMotionBlur(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radius: Float,
        vy: Float,
        color: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.30f),
        maxBlur: Float = 16f
    ) {
        val speed = abs(vy)
        if (speed < 0.03f) return

        val dirY = if (vy > 0f) 1f else -1f
        val blur = (speed * 1.30f).coerceIn(2.0f, maxBlur)
        val intensity = (speed / 10f).coerceIn(0.14f, 1.0f)
        val trailDist = (speed * 0.85f).coerceAtMost(25f)

        val shadowColor = color.multiplyAlpha(intensity * 0.65f)
        ShadowRenderer.drawShadow(
            context,
            x,
            y - dirY * trailDist * 0.55f,
            width,
            height,
            radius,
            blur,
            spread = 0.5f,
            color = shadowColor,
            offsetY = 0f
        )

        for (i in 1..2) {
            val t = i * 0.38f
            val ghostY = y - vy * t * 0.60f
            val ghostAlpha = (1f - t * 0.70f) * 0.22f * intensity * color.a
            if (ghostAlpha > 0.008f) {
                RoundedRect.draw(
                    context,
                    x,
                    ghostY,
                    width,
                    height,
                    CornerRadii(radius),
                    color.copy(a = ghostAlpha)
                )
            }
        }
    }
}
