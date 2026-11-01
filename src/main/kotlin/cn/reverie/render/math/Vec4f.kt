package cn.reverie.render.math

import kotlin.math.sqrt

data class Vec4f(val x: Float, val y: Float, val z: Float, val w: Float) {

    constructor(x: Number, y: Number, z: Number, w: Number) : this(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
    constructor(all: Float) : this(all, all, all, all)
    constructor() : this(0f, 0f, 0f, 0f)

    operator fun plus(other: Vec4f): Vec4f = Vec4f(x + other.x, y + other.y, z + other.z, w + other.w)
    operator fun minus(other: Vec4f): Vec4f = Vec4f(x - other.x, y - other.y, z - other.z, w - other.w)
    operator fun times(scalar: Float): Vec4f = Vec4f(x * scalar, y * scalar, z * scalar, w * scalar)
    operator fun times(other: Vec4f): Vec4f = Vec4f(x * other.x, y * other.y, z * other.z, w * other.w)
    operator fun div(scalar: Float): Vec4f = Vec4f(x / scalar, y / scalar, z / scalar, w / scalar)

    fun dot(other: Vec4f): Float = x * other.x + y * other.y + z * other.z + w * other.w

    fun length(): Float = sqrt(x * x + y * y + z * z + w * w)

    fun normalized(): Vec4f {
        val len = length()
        return if (len > 1e-6f) this / len else ZERO
    }

    fun lerp(target: Vec4f, t: Float): Vec4f = Vec4f(
        x + (target.x - x) * t,
        y + (target.y - y) * t,
        z + (target.z - z) * t,
        w + (target.w - w) * t
    )

    fun clamp(minVal: Float = 0f, maxVal: Float = 1f): Vec4f = Vec4f(
        x.coerceIn(minVal, maxVal),
        y.coerceIn(minVal, maxVal),
        z.coerceIn(minVal, maxVal),
        w.coerceIn(minVal, maxVal)
    )

    companion object {
        val ZERO = Vec4f(0f, 0f, 0f, 0f)
        val ONE = Vec4f(1f, 1f, 1f, 1f)
    }
}
