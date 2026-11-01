package cn.reverie.gui.clickgui

import kotlin.math.abs

/** A damped spring and invertible surface pose, independent of rendering. */
internal class ClickGuiSurfaceMotion {
    internal data class Pose(
        val a: Float, val b: Float, val c: Float,
        val d: Float, val e: Float, val f: Float
    ) {
        fun inversePoint(x: Float, y: Float): Pair<Float, Float> = inverseDelta(x - e, y - f)

        fun inverseDelta(x: Float, y: Float): Pair<Float, Float> {
            val det = a * d - b * c
            return Pair((d * x - c * y) / det, (a * y - b * x) / det)
        }
    }

    var depth = 0f
        private set
    val compression: Float get() = depth.coerceIn(0f, 1f)
    val isHeld: Boolean get() = heldButtons.isNotEmpty()

    private var velocity = 0f
    private var directionX = 0f
    private var directionY = 0f
    private var pressedAt = 0L
    private var lastAdvance = 0L
    private val heldButtons = mutableSetOf<Int>()

    fun press(x: Float, y: Float, width: Float, height: Float, button: Int, now: Long = System.nanoTime()) {
        directionX = (x / width.coerceAtLeast(1f) * 2f - 1f).coerceIn(-1f, 1f)
        directionY = (y / height.coerceAtLeast(1f) * 2f - 1f).coerceIn(-1f, 1f)
        heldButtons.add(button)
        pressedAt = now
        lastAdvance = now
        // Give even a quick tap an immediately visible inward impulse.
        depth = depth.coerceAtLeast(0.14f)
        velocity = velocity.coerceAtLeast(14f)
    }

    fun release(button: Int) {
        heldButtons.remove(button)
    }

    fun releaseAll() {
        heldButtons.clear()
    }

    fun reset() {
        heldButtons.clear()
        depth = 0f
        velocity = 0f
        directionX = 0f
        directionY = 0f
        pressedAt = 0L
        lastAdvance = 0L
    }

    fun advance(now: Long = System.nanoTime()) {
        if (lastAdvance == 0L) {
            lastAdvance = now
            return
        }
        var remaining = ((now - lastAdvance) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastAdvance = now
        val minimumPress = pressedAt != 0L && now - pressedAt < 90_000_000L
        val target = if (isHeld || minimumPress) 1f else 0f
        while (remaining > 0f) {
            val step = minOf(remaining, 1f / 120f)
            velocity += ((target - depth) * 470f - velocity * 25f) * step
            val nextDepth = depth + velocity * step
            depth = nextDepth.coerceIn(-0.18f, 1.24f)
            if (nextDepth != depth) velocity = 0f
            remaining -= step
        }
        if (target == 0f && abs(depth) < 0.001f && abs(velocity) < 0.01f) {
            depth = 0f
            velocity = 0f
        }
    }

    fun pose(x: Float, y: Float, width: Float, height: Float): Pose {
        val amount = depth
        val positive = compression
        val pivotX = x + width * (0.5f + directionX * 0.20f)
        val pivotY = y + height * (0.5f + directionY * 0.20f)
        val scale = 1f - amount * 0.045f
        val a = scale - abs(directionX) * positive * 0.008f
        val d = scale - abs(directionY) * positive * 0.012f
        val b = directionX * amount * (12f / width.coerceAtLeast(1f)).coerceAtMost(0.055f)
        val c = -directionY * amount * (12f / height.coerceAtLeast(1f)).coerceAtMost(0.055f)
        val e = pivotX + directionX * amount * 2f - a * pivotX - c * pivotY
        val f = pivotY + amount * (2.5f + directionY * 1.5f) - b * pivotX - d * pivotY
        return Pose(a, b, c, d, e, f)
    }
}
