package cn.reverie.render.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class MatrixStack2D(initialCapacity: Int = 32) {

    private var stack = FloatArray(initialCapacity * 6)
    private var depth = 0

    init {
        loadIdentity()
    }

    val currentMatrix: FloatArray
        get() {
            val offset = depth * 6
            return stack.copyOfRange(offset, offset + 6)
        }

    fun push(): MatrixStack2D {
        ensureCapacity(depth + 1)
        val srcOffset = depth * 6
        val dstOffset = (depth + 1) * 6
        System.arraycopy(stack, srcOffset, stack, dstOffset, 6)
        depth++
        return this
    }

    fun pop(): MatrixStack2D {
        if (depth > 0) {
            depth--
        }
        return this
    }

    fun loadIdentity(): MatrixStack2D {
        val o = depth * 6
        stack[o + 0] = 1f; stack[o + 1] = 0f
        stack[o + 2] = 0f; stack[o + 3] = 1f
        stack[o + 4] = 0f; stack[o + 5] = 0f
        return this
    }

    fun translate(x: Float, y: Float): MatrixStack2D {
        val o = depth * 6
        stack[o + 4] += stack[o + 0] * x + stack[o + 2] * y
        stack[o + 5] += stack[o + 1] * x + stack[o + 3] * y
        return this
    }

    fun translate(vec: Vec2f): MatrixStack2D = translate(vec.x, vec.y)

    fun scale(sx: Float, sy: Float): MatrixStack2D {
        val o = depth * 6
        stack[o + 0] *= sx
        stack[o + 1] *= sx
        stack[o + 2] *= sy
        stack[o + 3] *= sy
        return this
    }

    fun scale(scale: Float): MatrixStack2D = scale(scale, scale)

    fun rotate(radians: Float): MatrixStack2D {
        val c = cos(radians)
        val s = sin(radians)
        val o = depth * 6
        val m00 = stack[o + 0]
        val m10 = stack[o + 1]
        val m01 = stack[o + 2]
        val m11 = stack[o + 3]

        stack[o + 0] = m00 * c + m01 * s
        stack[o + 1] = m10 * c + m11 * s
        stack[o + 2] = m00 * -s + m01 * c
        stack[o + 3] = m10 * -s + m11 * c
        return this
    }

    fun rotateDegrees(degrees: Float): MatrixStack2D = rotate(Math.toRadians(degrees.toDouble()).toFloat())

    fun skewX(radians: Float): MatrixStack2D {
        val o = depth * 6
        val t = tan(radians)
        val m00 = stack[o + 0]
        val m10 = stack[o + 1]
        stack[o + 2] += m00 * t
        stack[o + 3] += m10 * t
        return this
    }

    fun skewY(radians: Float): MatrixStack2D {
        val o = depth * 6
        val t = tan(radians)
        val m01 = stack[o + 2]
        val m11 = stack[o + 3]
        stack[o + 0] += m01 * t
        stack[o + 1] += m11 * t
        return this
    }

    fun transformPoint(x: Float, y: Float): Vec2f {
        val o = depth * 6
        val tx = stack[o + 0] * x + stack[o + 2] * y + stack[o + 4]
        val ty = stack[o + 1] * x + stack[o + 3] * y + stack[o + 5]
        return Vec2f(tx, ty)
    }

    fun transformPoint(point: Vec2f): Vec2f = transformPoint(point.x, point.y)

    fun transformRect(rect: Rect2f): Rect2f {
        val p1 = transformPoint(rect.x, rect.y)
        val p2 = transformPoint(rect.x2, rect.y)
        val p3 = transformPoint(rect.x2, rect.y2)
        val p4 = transformPoint(rect.x, rect.y2)

        val minX = minOf(p1.x, p2.x, p3.x, p4.x)
        val minY = minOf(p1.y, p2.y, p3.y, p4.y)
        val maxX = maxOf(p1.x, p2.x, p3.x, p4.x)
        val maxY = maxOf(p1.y, p2.y, p3.y, p4.y)

        return Rect2f.fromBounds(minX, minY, maxX, maxY)
    }

    private fun ensureCapacity(minDepth: Int) {
        val requiredSize = (minDepth + 1) * 6
        if (requiredSize > stack.size) {
            val newStack = FloatArray(stack.size * 2)
            System.arraycopy(stack, 0, newStack, 0, stack.size)
            stack = newStack
        }
    }
}
