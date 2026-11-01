package cn.reverie.render.draw

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack

object Draw2D {

    val context: Long get() = RenderEngine.nanoVG.handle

    fun rect(x: Float, y: Float, width: Float, height: Float, color: NanoVGColor) {
        val path = NanoVGPath(context)
        path.begin()
        path.rect(x, y, width, height)
        path.fill(color)
    }

    fun rect(rect: Rect2f, color: NanoVGColor) = rect(rect.x, rect.y, rect.width, rect.height, color)

    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: NanoVGColor) {
        RoundedRect.draw(context, x, y, width, height, CornerRadii(radius), color)
    }

    fun roundedRect(rect: Rect2f, radius: Float, color: NanoVGColor) = roundedRect(rect.x, rect.y, rect.width, rect.height, radius, color)

    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radii: CornerRadii, color: NanoVGColor) {
        RoundedRect.draw(context, x, y, width, height, radii, color)
    }

    fun roundedOutline(x: Float, y: Float, width: Float, height: Float, radius: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        RoundedRect.drawOutline(context, x, y, width, height, CornerRadii(radius), color, strokeWidth)
    }

    fun roundedOutline(rect: Rect2f, radius: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) =
        roundedOutline(rect.x, rect.y, rect.width, rect.height, radius, color, strokeWidth)

    fun roundedOutline(x: Float, y: Float, width: Float, height: Float, radii: CornerRadii, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        RoundedRect.drawOutline(context, x, y, width, height, radii, color, strokeWidth)
    }

    fun roundedOutline(rect: Rect2f, radii: CornerRadii, color: NanoVGColor, strokeWidth: Float = 1.0f) =
        roundedOutline(rect.x, rect.y, rect.width, rect.height, radii, color, strokeWidth)

    fun circle(cx: Float, cy: Float, radius: Float, color: NanoVGColor) {
        val path = NanoVGPath(context)
        path.begin()
        path.circle(cx, cy, radius)
        path.fill(color)
    }

    fun circleOutline(cx: Float, cy: Float, radius: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        val path = NanoVGPath(context)
        path.begin()
        path.circle(cx, cy, radius)
        path.stroke(color, strokeWidth)
    }

    fun gradientLinear(x: Float, y: Float, width: Float, height: Float, radius: Float, startColor: NanoVGColor, endColor: NanoVGColor, horizontal: Boolean = false) {
        GradientRenderer.drawLinearRounded(context, x, y, width, height, CornerRadii(radius), startColor, endColor, horizontal)
    }

    fun gradientLinear(x: Float, y: Float, width: Float, height: Float, radii: CornerRadii, startColor: NanoVGColor, endColor: NanoVGColor, horizontal: Boolean = false) {
        GradientRenderer.drawLinearRounded(context, x, y, width, height, radii, startColor, endColor, horizontal)
    }

    fun gradientDiagonal(x: Float, y: Float, width: Float, height: Float, radius: Float, startColor: NanoVGColor, endColor: NanoVGColor) {
        GradientRenderer.drawDiagonalRounded(context, x, y, width, height, CornerRadii(radius), startColor, endColor)
    }

    fun shadow(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, blur: Float,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 2f,
        offsetX: Float = 0f,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) {
        ShadowRenderer.drawShadow(context, x, y, width, height, radius, blur, spread, color, offsetY, offsetX, innerCutout)
    }

    fun shadow(
        rect: Rect2f, radius: Float, blur: Float,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 2f,
        offsetX: Float = 0f,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) = shadow(rect.x, rect.y, rect.width, rect.height, radius, blur, color, offsetY, offsetX, spread, innerCutout)

    fun shadow(
        x: Float, y: Float, width: Float, height: Float,
        radii: CornerRadii, blur: Float,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 2f,
        offsetX: Float = 0f,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) {
        ShadowRenderer.drawShadow(context, x, y, width, height, radii, blur, spread, color, offsetY, offsetX, innerCutout)
    }

    fun shadow(
        rect: Rect2f, radii: CornerRadii, blur: Float,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 2f,
        offsetX: Float = 0f,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) = shadow(rect.x, rect.y, rect.width, rect.height, radii, blur, color, offsetY, offsetX, spread, innerCutout)

    fun glow(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, blur: Float,
        color: NanoVGColor,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) {
        ShadowRenderer.drawGlow(context, x, y, width, height, radius, blur, color, spread, innerCutout)
    }

    fun glow(
        rect: Rect2f, radius: Float, blur: Float,
        color: NanoVGColor,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) = glow(rect.x, rect.y, rect.width, rect.height, radius, blur, color, spread, innerCutout)

    fun elevation(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float,
        elevation: Int = 2,
        color: NanoVGColor = NanoVGColor.BLACK,
        innerCutout: Boolean = false
    ) {
        ShadowRenderer.drawElevation(context, x, y, width, height, radius, elevation, color, innerCutout)
    }

    fun elevation(
        rect: Rect2f, radius: Float,
        elevation: Int = 2,
        color: NanoVGColor = NanoVGColor.BLACK,
        innerCutout: Boolean = false
    ) = elevation(rect.x, rect.y, rect.width, rect.height, radius, elevation, color, innerCutout)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: NanoVGColor, width: Float = 1.0f) {
        LineRenderer.drawLine(context, x1, y1, x2, y2, color, width)
    }

    fun imageRounded(
        x: Float, y: Float,
        width: Float, height: Float,
        radius: Float,
        textureId: Int,
        u0: Float = 0f, v0: Float = 0f,
        u1: Float = 1f, v1: Float = 1f,
        tintColor: NanoVGColor = NanoVGColor.WHITE
    ) {
        val nvgImg = cn.reverie.render.nanovg.NanoVGImage.createFromGLTexture(context, textureId, width.toInt(), height.toInt())
        try {
            MemoryStack.stackPush().use { stack ->
                val paint = NVGPaint.malloc(stack)
                NanoVGPaint.imagePattern(context, x, y, width, height, 0f, nvgImg.imageHandle, tintColor.a, paint)
                RoundedRect.draw(context, x, y, width, height, CornerRadii(radius), paint)
            }
        } finally {
            nvgImg.close()
        }
    }
}
