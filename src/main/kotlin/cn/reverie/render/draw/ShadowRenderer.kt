package cn.reverie.render.draw

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.NVG_HOLE
import org.lwjgl.system.MemoryStack
import kotlin.math.max

/** Soft two-component shadows rendered with NanoVG box gradients. */
object ShadowRenderer {

    fun drawShadow(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadii: Float,
        blur: Float,
        spread: Float = 0f,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 0f,
        offsetX: Float = 0f,
        innerCutout: Boolean = false
    ) = drawShadow(
        context, x, y, width, height, CornerRadii(cornerRadii), blur, spread,
        color, offsetY, offsetX, innerCutout
    )

    fun drawShadow(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        blur: Float,
        spread: Float = 0f,
        color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.4f),
        offsetY: Float = 0f,
        offsetX: Float = 0f,
        innerCutout: Boolean = false
    ) {
        val alpha = color.a.coerceIn(0f, 1f)
        val blurRadius = blur.coerceAtLeast(0f)
        if (width <= 0f || height <= 0f || alpha <= 0.001f) return
        if (blurRadius <= 0.01f && spread <= 0f) return

        // A broad, low-opacity ambient veil gives separation from the background.
        val ambientAlpha = alpha * 0.34f
        // The tighter key shadow restores depth near the edge without a hard outline.
        val contactAlpha = ((alpha - ambientAlpha) / (1f - ambientAlpha).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
        val ambientBlur = max(2f, blurRadius * 1.12f)
        val contactBlur = max(1.25f, blurRadius * 0.42f)
        val ambientSpread = spread + blurRadius * 0.035f
        val contactSpread = spread * 0.35f

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            drawGradientLayer(
                context, x, y, width, height, radii,
                ambientBlur, ambientSpread,
                offsetX * 0.55f, offsetY * 0.55f,
                color.withAlpha(ambientAlpha), innerCutout, paint
            )
            drawGradientLayer(
                context, x, y, width, height, radii,
                contactBlur, contactSpread,
                offsetX, offsetY,
                color.withAlpha(contactAlpha), innerCutout, paint
            )
        }
    }

    private fun drawGradientLayer(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        radii: CornerRadii,
        blur: Float,
        spread: Float,
        offsetX: Float,
        offsetY: Float,
        color: NanoVGColor,
        innerCutout: Boolean,
        paint: NVGPaint
    ) {
        val sx = x + offsetX - spread
        val sy = y + offsetY - spread
        val sw = (width + spread * 2f).coerceAtLeast(0.5f)
        val sh = (height + spread * 2f).coerceAtLeast(0.5f)
        val expandedRadii = CornerRadii(
            (radii.topLeft + spread).coerceAtLeast(0f),
            (radii.topRight + spread).coerceAtLeast(0f),
            (radii.bottomRight + spread).coerceAtLeast(0f),
            (radii.bottomLeft + spread).coerceAtLeast(0f)
        )
        val averageRadius = (expandedRadii.topLeft + expandedRadii.topRight + expandedRadii.bottomRight + expandedRadii.bottomLeft) * 0.25f
        val feather = max(1.5f, blur * 2f)
        val pad = max(blur * 1.8f, feather + 3f)

        NanoVGPaint.boxGradient(
            context, sx, sy, sw, sh, averageRadius, feather,
            color, color.withAlpha(0f), paint
        )

        val path = NanoVGPath(context)
        path.begin()
        path.rect(sx - pad, sy - pad, sw + pad * 2f, sh + pad * 2f)
        if (innerCutout) {
            appendRoundedRect(path, x, y, width, height, radii)
            path.pathWinding(NVG_HOLE)
        }
        path.fill(paint)
    }

    private fun appendRoundedRect(path: NanoVGPath, x: Float, y: Float, width: Float, height: Float, radii: CornerRadii) {
        if (radii.isUniform) path.roundedRect(x, y, width, height, radii.topLeft)
        else path.roundedRectVarying(x, y, width, height, radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft)
    }

    fun drawGlow(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadii: Float,
        blur: Float,
        color: NanoVGColor,
        spread: Float = 0f,
        innerCutout: Boolean = false
    ) = drawShadow(context, x, y, width, height, cornerRadii, blur, spread, color, 0f, 0f, innerCutout)

    fun drawElevation(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        cornerRadii: Float,
        elevation: Int = 2,
        color: NanoVGColor = NanoVGColor.BLACK,
        innerCutout: Boolean = false
    ) {
        val (blur, offsetY, opacity) = when (elevation.coerceIn(1, 5)) {
            1 -> Triple(4f, 1f, 0.18f)
            2 -> Triple(8f, 2.5f, 0.24f)
            3 -> Triple(14f, 5f, 0.30f)
            4 -> Triple(21f, 8f, 0.35f)
            else -> Triple(30f, 12f, 0.40f)
        }
        drawShadow(
            context, x, y, width, height, cornerRadii, blur,
            spread = blur * 0.04f,
            color = color.withAlpha(color.a.coerceIn(0f, 1f) * opacity),
            offsetY = offsetY,
            innerCutout = innerCutout
        )
    }
}
