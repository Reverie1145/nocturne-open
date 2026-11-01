package cn.reverie.render.math

import kotlin.math.*

object MathUtils {

    const val PI_F = Math.PI.toFloat()
    const val HALF_PI_F = (Math.PI / 2.0).toFloat()
    const val TWO_PI_F = (Math.PI * 2.0).toFloat()

    fun clamp(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)
    fun clamp(value: Double, min: Double, max: Double): Double = value.coerceIn(min, max)
    fun clamp(value: Int, min: Int, max: Int): Int = value.coerceIn(min, max)

    fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
    fun lerp(start: Double, end: Double, t: Double): Double = start + (end - start) * t

    fun map(value: Float, inMin: Float, inMax: Float, outMin: Float, outMax: Float): Float {
        if (abs(inMax - inMin) < 1e-7f) return outMin
        val t = (value - inMin) / (inMax - inMin)
        return outMin + t * (outMax - outMin)
    }

    fun step(edge: Float, x: Float): Float = if (x < edge) 0f else 1f

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun smootherstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f)
        return t * t * t * (t * (t * 6f - 15f) + 10f)
    }

    fun fract(x: Float): Float = x - floor(x)

    fun radians(degrees: Float): Float = degrees * (PI_F / 180f)
    fun degrees(radians: Float): Float = radians * (180f / PI_F)

    fun roundToDecimal(value: Float, decimals: Int): Float {
        val factor = 10.0.pow(decimals).toFloat()
        return round(value * factor) / factor
    }

    fun interpolateColor(color1: Int, color2: Int, t: Float): Int {
        val a1 = (color1 ushr 24) and 0xFF
        val r1 = (color1 ushr 16) and 0xFF
        val g1 = (color1 ushr 8) and 0xFF
        val b1 = color1 and 0xFF

        val a2 = (color2 ushr 24) and 0xFF
        val r2 = (color2 ushr 16) and 0xFF
        val g2 = (color2 ushr 8) and 0xFF
        val b2 = color2 and 0xFF

        val a = (a1 + (a2 - a1) * t).roundToInt().coerceIn(0, 255)
        val r = (r1 + (r2 - r1) * t).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * t).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * t).roundToInt().coerceIn(0, 255)

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
