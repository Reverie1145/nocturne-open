package cn.reverie.render.math

import kotlin.math.sqrt

class BezierCurve(
    val p0: Vec2f,
    val p1: Vec2f,
    val p2: Vec2f,
    val p3: Vec2f? = null
) {
    val isCubic: Boolean get() = p3 != null

    fun evaluate(t: Float): Vec2f {
        val u = 1f - t
        val tt = t * t
        val uu = u * u

        return if (p3 != null) {

            val uuu = uu * u
            val ttt = tt * t
            p0 * uuu + p1 * (3f * uu * t) + p2 * (3f * u * tt) + p3 * ttt
        } else {

            p0 * uu + p1 * (2f * u * t) + p2 * tt
        }
    }

    fun tangent(t: Float): Vec2f {
        val u = 1f - t
        val tt = t * t
        val uu = u * u

        val tan = if (p3 != null) {

            (p1 - p0) * (3f * uu) + (p2 - p1) * (6f * u * t) + (p3 - p2) * (3f * tt)
        } else {

            (p1 - p0) * (2f * u) + (p2 - p1) * (2f * t)
        }
        return tan.normalized()
    }

    fun normal(t: Float): Vec2f {
        val tan = tangent(t)
        return Vec2f(-tan.y, tan.x)
    }

    fun approximateLength(subdivisions: Int = 20): Float {
        var length = 0f
        var lastPoint = evaluate(0f)
        for (i in 1..subdivisions) {
            val t = i.toFloat() / subdivisions
            val currentPoint = evaluate(t)
            length += lastPoint.distance(currentPoint)
            lastPoint = currentPoint
        }
        return length
    }

    fun split(t: Float): Pair<BezierCurve, BezierCurve> {
        val u = 1f - t
        return if (p3 != null) {
            val p01 = p0.lerp(p1, t)
            val p12 = p1.lerp(p2, t)
            val p23 = p2.lerp(p3, t)

            val p012 = p01.lerp(p12, t)
            val p123 = p12.lerp(p23, t)

            val p0123 = p012.lerp(p123, t)

            Pair(
                BezierCurve(p0, p01, p012, p0123),
                BezierCurve(p0123, p123, p23, p3)
            )
        } else {
            val p01 = p0.lerp(p1, t)
            val p12 = p1.lerp(p2, t)
            val p012 = p01.lerp(p12, t)

            Pair(
                BezierCurve(p0, p01, p012),
                BezierCurve(p012, p12, p2)
            )
        }
    }
}
