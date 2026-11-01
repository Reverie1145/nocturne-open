package cn.reverie.render.math

import kotlin.math.max
import kotlin.math.min

data class Rect2f(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    constructor(x: Number, y: Number, width: Number, height: Number) : this(
        x.toFloat(),
        y.toFloat(),
        width.toFloat(),
        height.toFloat()
    )

    constructor() : this(0f, 0f, 0f, 0f)

    val x2: Float get() = x + width
    val y2: Float get() = y + height
    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f
    val center: Vec2f get() = Vec2f(centerX, centerY)
    val position: Vec2f get() = Vec2f(x, y)
    val size: Vec2f get() = Vec2f(width, height)

    val isEmpty: Boolean get() = width <= 0f || height <= 0f

    fun contains(px: Float, py: Float): Boolean {
        return px >= x && px <= x2 && py >= y && py <= y2
    }

    fun contains(point: Vec2f): Boolean = contains(point.x, point.y)

    fun contains(other: Rect2f): Boolean {
        return other.x >= x && other.x2 <= x2 && other.y >= y && other.y2 <= y2
    }

    fun intersects(other: Rect2f): Boolean {
        return x < other.x2 && x2 > other.x && y < other.y2 && y2 > other.y
    }

    fun intersect(other: Rect2f): Rect2f {
        val nx = max(x, other.x)
        val ny = max(y, other.y)
        val nx2 = min(x2, other.x2)
        val ny2 = min(y2, other.y2)
        val nw = max(0f, nx2 - nx)
        val nh = max(0f, ny2 - ny)
        return Rect2f(nx, ny, nw, nh)
    }

    fun union(other: Rect2f): Rect2f {
        if (isEmpty) return other
        if (other.isEmpty) return this
        val nx = min(x, other.x)
        val ny = min(y, other.y)
        val nx2 = max(x2, other.x2)
        val ny2 = max(y2, other.y2)
        return Rect2f(nx, ny, nx2 - nx, ny2 - ny)
    }

    fun offset(dx: Float, dy: Float): Rect2f = Rect2f(x + dx, y + dy, width, height)
    fun offset(delta: Vec2f): Rect2f = offset(delta.x, delta.y)

    fun expand(amount: Float): Rect2f = Rect2f(x - amount, y - amount, width + amount * 2f, height + amount * 2f)
    fun expand(horizontal: Float, vertical: Float): Rect2f = Rect2f(x - horizontal, y - vertical, width + horizontal * 2f, height + vertical * 2f)

    fun shrink(amount: Float): Rect2f = expand(-amount)
    fun shrink(horizontal: Float, vertical: Float): Rect2f = expand(-horizontal, -vertical)

    fun clamp(point: Vec2f): Vec2f = Vec2f(
        point.x.coerceIn(x, x2),
        point.y.coerceIn(y, y2)
    )

    companion object {
        val EMPTY = Rect2f(0f, 0f, 0f, 0f)

        fun fromBounds(minX: Float, minY: Float, maxX: Float, maxY: Float): Rect2f {
            return Rect2f(minX, minY, max(0f, maxX - minX), max(0f, maxY - minY))
        }
    }
}
