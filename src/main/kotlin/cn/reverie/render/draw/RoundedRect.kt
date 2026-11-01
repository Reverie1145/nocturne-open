package cn.reverie.render.draw

import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGContext
import org.lwjgl.nanovg.NVGPaint

object RoundedRect {

    fun draw(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        color: NanoVGColor
    ) {
        val path = cn.reverie.render.nanovg.NanoVGPath(context)
        path.begin()
        if (radii.isUniform) {
            path.roundedRect(x, y, width, height, radii.topLeft)
        } else {
            path.roundedRectVarying(x, y, width, height, radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
        }
        path.fill(color)
    }

    fun draw(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        paint: NVGPaint
    ) {
        val path = cn.reverie.render.nanovg.NanoVGPath(context)
        path.begin()
        if (radii.isUniform) {
            path.roundedRect(x, y, width, height, radii.topLeft)
        } else {
            path.roundedRectVarying(x, y, width, height, radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
        }
        path.fill(paint)
    }

    fun drawOutline(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        color: NanoVGColor,
        strokeWidth: Float = 1.0f
    ) {
        val path = cn.reverie.render.nanovg.NanoVGPath(context)
        path.begin()
        val half = strokeWidth * 0.5f
        if (radii.isUniform) {
            path.roundedRect(x + half, y + half, width - strokeWidth, height - strokeWidth, maxOf(0f, radii.topLeft - half))
        } else {
            path.roundedRectVarying(
                x + half, y + half, width - strokeWidth, height - strokeWidth,
                maxOf(0f, radii.topLeft - half),
                maxOf(0f, radii.topRight - half),
                maxOf(0f, radii.bottomRight - half),
                maxOf(0f, radii.bottomLeft - half)
            )
        }
        path.stroke(color, strokeWidth)
    }
}
