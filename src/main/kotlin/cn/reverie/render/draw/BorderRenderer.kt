package cn.reverie.render.draw

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack

object BorderRenderer {

    fun drawSolid(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        color: NanoVGColor,
        strokeWidth: Float = 1.0f
    ) {
        RoundedRect.drawOutline(context, x, y, width, height, radii, color, strokeWidth)
    }

    fun drawGradient(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        startColor: NanoVGColor,
        endColor: NanoVGColor,
        strokeWidth: Float = 1.0f
    ) {
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(context, x, y, x, y + height, startColor, endColor, paint)

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
            path.stroke(paint, strokeWidth)
        }
    }
}
