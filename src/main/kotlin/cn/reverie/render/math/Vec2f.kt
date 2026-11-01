package cn.reverie.render.math

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2f(val x: Float, val y: Float) {

    constructor(x: Number, y: Number) : this(x.toFloat(), y.toFloat())
    constructor() : this(0f, 0f)

    operator fun plus(other: Vec2f): Vec2f = Vec2f(x + other.x, y + other.y)
    operator fun plus(scalar: Float): Vec2f = Vec2f(x + scalar, y + scalar)

    operator fun minus(other: Vec2f): Vec2f = Vec2f(x - other.x, y - other.y)
    operator fun minus(scalar: Float): Vec2f = Vec2f(x - scalar, y - scalar)

    operator fun times(other: Vec2f): Vec2f = Vec2f(x * other.x, y * other.y)
    operator fun times(scalar: Float): Vec2f = Vec2f(x * scalar, y * scalar)

    operator fun div(other: Vec2f): Vec2f = Vec2f(x / other.x, y / other.y)
    operator fun div(scalar: Float): Vec2f = Vec2f(x / scalar, y / scalar)

    operator fun unaryMinus(): Vec2f = Vec2f(-x, -y)

    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())

    fun distanceSquared(other: Vec2f): Float {
        val dx = x - other.x
        val dy = y - other.y
        return dx * dx + dy * dy
    }

    fun distance(other: Vec2f): Float = sqrt(distanceSquared(other))

    fun dot(other: Vec2f): Float = x * other.x + y * other.y
    fun cross(other: Vec2f): Float = x * other.y - y * other.x

    fun normalized(): Vec2f {
        val len = length()
        return if (len > 1e-6f) this / len else ZERO
    }

    fun lerp(target: Vec2f, t: Float): Vec2f = Vec2f(
        x + (target.x - x) * t,
        y + (target.y - y) * t
    )

    fun rotate(radians: Float): Vec2f {
        val c = cos(radians)
        val s = sin(radians)
        return Vec2f(x * c - y * s, x * s + y * c)
    }

    fun angle(): Float = atan2(y, x)

    fun angleTo(other: Vec2f): Float = atan2(cross(other), dot(other))

    fun clamp(min: Vec2f, max: Vec2f): Vec2f = Vec2f(
        x.coerceIn(min.x, max.x),
        y.coerceIn(min.y, max.y)
    )

    companion object {
        val ZERO = Vec2f(0f, 0f)
        val ONE = Vec2f(1f, 1f)
        val UNIT_X = Vec2f(1f, 0f)
        val UNIT_Y = Vec2f(0f, 1f)
    }
}
