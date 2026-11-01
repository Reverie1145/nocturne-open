package cn.reverie.render.animation

import kotlin.math.abs

class CubicBezier(
    private val x1: Float,
    private val y1: Float,
    private val x2: Float,
    private val y2: Float
) : (Float) -> Float {

    companion object {

        val EASE_IN_OUT = CubicBezier(0.65f, 0.0f, 0.35f, 1.0f)
        val EASE_IN_OUT_SMOOTH = CubicBezier(0.42f, 0.0f, 0.58f, 1.0f)
        val EASE_OUT = CubicBezier(0.25f, 0.1f, 0.25f, 1.0f)
        val EASE_IN = CubicBezier(0.42f, 0.0f, 1.0f, 1.0f)
        val EASE_OUT_BACK = CubicBezier(0.34f, 1.56f, 0.64f, 1.0f)
    }

    override fun invoke(progress: Float): Float {
        val clampedX = progress.coerceIn(0.0f, 1.0f)
        if (clampedX == 0.0f || clampedX == 1.0f) return clampedX
        val t = solveCurveX(clampedX)
        return sampleCurveY(t)
    }

    private fun sampleCurveX(t: Float): Float {
        val ax = 1.0f - 3.0f * x2 + 3.0f * x1
        val bx = 3.0f * x2 - 6.0f * x1
        val cx = 3.0f * x1
        return ((ax * t + bx) * t + cx) * t
    }

    private fun sampleCurveY(t: Float): Float {
        val ay = 1.0f - 3.0f * y2 + 3.0f * y1
        val by = 3.0f * y2 - 6.0f * y1
        val cy = 3.0f * y1
        return ((ay * t + by) * t + cy) * t
    }

    private fun sampleCurveDerivativeX(t: Float): Float {
        val ax = 1.0f - 3.0f * x2 + 3.0f * x1
        val bx = 3.0f * x2 - 6.0f * x1
        val cx = 3.0f * x1
        return (3.0f * ax * t + 2.0f * bx) * t + cx
    }

    private fun solveCurveX(x: Float): Float {
        var t = x

        for (i in 0 until 8) {
            val currentX = sampleCurveX(t) - x
            if (abs(currentX) < 1e-6f) return t
            val dX = sampleCurveDerivativeX(t)
            if (abs(dX) < 1e-6f) break
            t -= currentX / dX
        }

        var t0 = 0.0f
        var t1 = 1.0f
        t = x
        while (t0 < t1) {
            val currentX = sampleCurveX(t)
            if (abs(currentX - x) < 1e-6f) return t
            if (x > currentX) {
                t0 = t
            } else {
                t1 = t
            }
            t = (t1 + t0) * 0.5f
        }
        return t
    }
}
