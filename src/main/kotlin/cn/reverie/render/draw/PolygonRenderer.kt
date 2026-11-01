package cn.reverie.render.draw

import cn.reverie.render.math.Vec2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import kotlin.math.cos
import kotlin.math.sin

object PolygonRenderer {

    fun drawRegularPolygon(
        context: Long,
        centerX: Float,
        centerY: Float,
        radius: Float,
        sides: Int,
        color: NanoVGColor,
        rotationRadians: Float = 0f
    ) {
        if (sides < 3) return
        val path = NanoVGPath(context)
        path.begin()

        val angleStep = (Math.PI * 2.0 / sides).toFloat()
        for (i in 0 until sides) {
            val angle = rotationRadians + i * angleStep
            val px = centerX + cos(angle) * radius
            val py = centerY + sin(angle) * radius

            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        path.fill(color)
    }

    fun drawTriangle(
        context: Long,
        p1: Vec2f,
        p2: Vec2f,
        p3: Vec2f,
        color: NanoVGColor
    ) {
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(p1.x, p1.y)
        path.lineTo(p2.x, p2.y)
        path.lineTo(p3.x, p3.y)
        path.close()
        path.fill(color)
    }
}
