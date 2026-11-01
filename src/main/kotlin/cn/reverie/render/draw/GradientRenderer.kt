package cn.reverie.render.draw

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack

object GradientRenderer {

    fun drawDiagonalRounded(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        startColor: NanoVGColor,
        endColor: NanoVGColor
    ) {
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(
                context,
                x, y + height,
                x + width, y,
                startColor, endColor,
                paint
            )
            RoundedRect.draw(context, x, y, width, height, radii, paint)
        }
    }

    fun drawLinearRounded(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        startColor: NanoVGColor,
        endColor: NanoVGColor,
        horizontal: Boolean = false
    ) {
        val sx = x
        val sy = y
        val ex = if (horizontal) x + width else x
        val ey = if (horizontal) y else y + height

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(context, sx, sy, ex, ey, startColor, endColor, paint)
            RoundedRect.draw(context, x, y, width, height, radii, paint)
        }
    }

    fun drawRadialRounded(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        innerColor: NanoVGColor,
        outerColor: NanoVGColor,
        inRadius: Float = 0f,
        outRadius: Float = maxOf(width, height) * 0.5f
    ) {
        val cx = x + width * 0.5f
        val cy = y + height * 0.5f

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.radialGradient(context, cx, cy, inRadius, outRadius, innerColor, outerColor, paint)
            RoundedRect.draw(context, x, y, width, height, radii, paint)
        }
    }
}
