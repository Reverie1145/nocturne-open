package cn.reverie.render.draw

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGImage
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.texture.NitroImages
import cn.reverie.render.theme.Theme
import com.mojang.blaze3d.opengl.GlTexture
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.world.entity.LivingEntity
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import kotlin.math.*

object SkiaRenderEngine {

    val context: Long get() = RenderEngine.nanoVG.handle

    fun genSquirclePath(path: NanoVGPath, x: Float, y: Float, w: Float, h: Float, r: Float): NanoVGPath {
        val radius = minOf(w / 3.2f, h / 3.2f, r)

        path.begin()
        path.moveTo(x, y + 1.6f * radius)
        path.bezierTo(x, y + 1.04f * radius, x, y + 0.76f * radius, x + 0.109f * radius, y + 0.546f * radius)
        path.bezierTo(x + 0.205f * radius, y + 0.358f * radius, x + 0.358f * radius, y + 0.205f * radius, x + 0.546f * radius, y + 0.109f * radius)
        path.bezierTo(x + 0.76f * radius, y, x + 1.04f * radius, y, x + 1.6f * radius, y)

        path.lineTo(x + w - 1.6f * radius, y)
        path.bezierTo(x + w - 1.04f * radius, y, x + w - 0.76f * radius, y, x + w - 0.546f * radius, y + 0.109f * radius)
        path.bezierTo(x + w - 0.358f * radius, y + 0.205f * radius, x + w - 0.205f * radius, y + 0.358f * radius, x + w - 0.109f * radius, y + 0.546f * radius)
        path.bezierTo(x + w, y + 0.76f * radius, x + w, y + 1.04f * radius, x + w, y + 1.6f * radius)

        path.lineTo(x + w, y + h - 1.6f * radius)
        path.bezierTo(x + w, y + h - 1.04f * radius, x + w, y + h - 0.76f * radius, x + w - 0.109f * radius, y + h - 0.546f * radius)
        path.bezierTo(x + w - 0.205f * radius, y + h - 0.358f * radius, x + w - 0.358f * radius, y + h - 0.205f * radius, x + w - 0.546f * radius, y + h - 0.109f * radius)
        path.bezierTo(x + w - 0.76f * radius, y + h, x + w - 1.04f * radius, y + h, x + w - 1.6f * radius, y + h)

        path.lineTo(x + 1.6f * radius, y + h)
        path.bezierTo(x + 1.04f * radius, y + h, x + 0.76f * radius, y + h, x + 0.546f * radius, y + h - 0.109f * radius)
        path.bezierTo(x + 0.358f * radius, y + h - 0.205f * radius, x + 0.205f * radius, y + h - 0.358f * radius, x + 0.109f * radius, y + h - 0.546f * radius)
        path.bezierTo(x, y + h - 0.76f * radius, x, y + h - 1.04f * radius, x, y + h - 1.6f * radius)

        path.close()
        return path
    }

    fun drawSquircle(x: Float, y: Float, w: Float, h: Float, r: Float, color: NanoVGColor) {
        genSquirclePath(NanoVGPath(context), x, y, w, h, r).fill(color)
    }

    fun drawSquircleOutline(x: Float, y: Float, w: Float, h: Float, r: Float, color: NanoVGColor, strokeWidth: Float = 1.0f) {
        genSquirclePath(NanoVGPath(context), x, y, w, h, r).stroke(color, strokeWidth)
    }

    fun drawRoundRectOutlineWithGradient(x: Float, y: Float, w: Float, h: Float, r: Float, strokeWidth: Float, color1: NanoVGColor, color2: NanoVGColor) {
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(context, x, y, x + w, y, color1, color2, paint)
            val path = NanoVGPath(context)
            path.begin()
            path.roundedRect(x, y, w, h, r)
            path.stroke(paint, strokeWidth)
        }
    }

    fun drawSquircleBottomRounded(x: Float, y: Float, w: Float, h: Float, r: Float, color: NanoVGColor) {
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(x, y)
        path.lineTo(x + w, y)
        path.lineTo(x + w, y + h - 1.6f * r)
        path.bezierTo(x + w, y + h - 1.04f * r, x + w, y + h - 0.76f * r, x + w - 0.109f * r, y + h - 0.546f * r)
        path.bezierTo(x + w - 0.205f * r, y + h - 0.358f * r, x + w - 0.358f * r, y + h - 0.205f * r, x + w - 0.546f * r, y + h - 0.109f * r)
        path.bezierTo(x + w - 0.76f * r, y + h, x + w - 1.04f * r, y + h, x + w - 1.6f * r, y + h)
        path.lineTo(x + 1.6f * r, y + h)
        path.bezierTo(x + 1.04f * r, y + h, x + 0.76f * r, y + h, x + 0.546f * r, y + h - 0.109f * r)
        path.bezierTo(x + 0.358f * r, y + h - 0.205f * r, x + 0.205f * r, y + h - 0.358f * r, x + 0.109f * r, y + h - 0.546f * r)
        path.bezierTo(x, y + h - 0.76f * r, x, y + h - 1.04f * r, x, y + h - 1.6f * r)
        path.close()
        path.fill(color)
    }

    fun drawRoundedHead(entity: LivingEntity, x: Float, y: Float, width: Float, height: Float, radius: Float, alpha: Float) {
        val mc = Minecraft.getInstance()
        var drawn = false

        if (entity is AbstractClientPlayer) {
            try {
                val skin = entity.skin
                val texture = mc.textureManager.getTexture(skin.body().texturePath())
                val glId = (texture.texture as? GlTexture)?.glId() ?: 0

                if (glId > 0) {
                    val nvgImg = NitroImages.fromGLTexture(context, glId, 64, 64) ?: return

                    MemoryStack.stackPush().use { stack ->
                        val paint = NVGPaint.malloc(stack)
                        val s = width / 8f
                        val ox = x - (8f * s)
                        val oy = y - (8f * s)
                        val sw = 64f * s
                        val sh = 64f * s
                        NanoVGPaint.imagePattern(context, ox, oy, sw, sh, 0f, nvgImg.imageHandle, alpha, paint)
                        genSquirclePath(NanoVGPath(context), x, y, width, height, radius).fill(paint)
                    }

                    if (entity.hurtTime > 0) {
                        val hurtAlpha = min(entity.hurtTime * 18, 255) / 255f * alpha
                        drawSquircle(x, y, width, height, radius, NanoVGColor(1f, 0f, 0f, hurtAlpha))
                    }
                    drawn = true
                }
            } catch (ignored: Throwable) {
            }
        }

        if (!drawn) {
            drawSquircle(x, y, width, height, radius, NanoVGColor(0.15f, 0.16f, 0.22f, 0.90f * alpha))
            val font = RenderEngine.fontRenderer
            val initial = entity.name.string.take(1).uppercase()
            font.drawString(initial, x + width * 0.5f, y + height * 0.5f, NanoVGColor(1f, 1f, 1f, alpha), 9f, cn.reverie.render.font.TextAlignment.MIDDLE_CENTER)
        }
    }

    fun drawRoundRectWithGradient(x: Float, y: Float, width: Float, height: Float, radius: Float, color1: NanoVGColor, color2: NanoVGColor) {
        Draw2D.gradientLinear(x, y, width, height, radius, color1, color2, horizontal = true)
    }

    fun drawSquareProgressBox(
        x: Float, y: Float,
        size: Float,
        stroke: Float,
        progress: Float,
        radius: Float,
        color1: NanoVGColor,
        color2: NanoVGColor
    ) {
        val inset = stroke / 2f
        val innerSize = size - stroke

        val bgPath = NanoVGPath(context)
        bgPath.begin()
        bgPath.roundedRect(x + inset, y + inset, innerSize, innerSize, radius)
        bgPath.stroke(NanoVGColor(1f, 1f, 1f, 0.12f), stroke)

        if (progress <= 0.001f) return

        val clampedProgress = progress.coerceIn(0f, 1f)
        val cornerArc = (Math.PI.toFloat() * radius / 2f)
        val straightEdge = innerSize - radius * 2f
        val totalPerimeter = straightEdge * 4f + cornerArc * 4f
        val drawLength = totalPerimeter * clampedProgress

        val centerX = x + size / 2f
        val startY = y + inset

        val pPath = NanoVGPath(context)
        pPath.begin()
        pPath.moveTo(centerX, startY)

        var remaining = drawLength
        val seg1 = innerSize / 2f - radius
        if (remaining > 0f) {
            val d = min(remaining, seg1)
            pPath.lineTo(centerX + d, startY)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, cornerArc)
            val sweep = (d / cornerArc) * (Math.PI.toFloat() / 2f)
            pPath.arc(x + size - inset - radius, y + inset + radius, radius, -Math.PI.toFloat() / 2f, -Math.PI.toFloat() / 2f + sweep, NVG_CW)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, straightEdge)
            pPath.lineTo(x + size - inset, y + inset + radius + d)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, cornerArc)
            val sweep = (d / cornerArc) * (Math.PI.toFloat() / 2f)
            pPath.arc(x + size - inset - radius, y + size - inset - radius, radius, 0f, sweep, NVG_CW)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, straightEdge)
            pPath.lineTo(x + size - inset - radius - d, y + size - inset)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, cornerArc)
            val sweep = (d / cornerArc) * (Math.PI.toFloat() / 2f)
            pPath.arc(x + inset + radius, y + size - inset - radius, radius, Math.PI.toFloat() / 2f, Math.PI.toFloat() / 2f + sweep, NVG_CW)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, straightEdge)
            pPath.lineTo(x + inset, y + size - inset - radius - d)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, cornerArc)
            val sweep = (d / cornerArc) * (Math.PI.toFloat() / 2f)
            pPath.arc(x + inset + radius, y + inset + radius, radius, Math.PI.toFloat(), Math.PI.toFloat() + sweep, NVG_CW)
            remaining -= d
        }
        if (remaining > 0f) {
            val d = min(remaining, seg1)
            pPath.lineTo(x + inset + radius + d, y + inset)
        }

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(context, x, y, x + size, y + size, color1, color2, paint)
            pPath.stroke(paint, stroke)
        }
    }

    fun drawCircularProgress(
        centerX: Float,
        centerY: Float,
        radius: Float,
        stroke: Float,
        progress: Float,
        color1: NanoVGColor,
        color2: NanoVGColor
    ) {

        val bgPath = NanoVGPath(context)
        bgPath.begin()
        bgPath.circle(centerX, centerY, radius)
        bgPath.stroke(NanoVGColor(1f, 1f, 1f, 0.12f), stroke)

        val clamped = progress.coerceIn(0f, 1f)
        if (clamped <= 0.001f) return

        val startAngle = -Math.PI.toFloat() / 2f
        val endAngle = startAngle + (Math.PI.toFloat() * 2f * clamped)

        nvgLineCap(context, NVG_ROUND)
        val arcPath = NanoVGPath(context)
        arcPath.begin()
        arcPath.arc(centerX, centerY, radius, startAngle, endAngle, NVG_CW)

        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.linearGradient(context, centerX - radius, centerY - radius, centerX + radius, centerY + radius, color1, color2, paint)
            arcPath.stroke(paint, stroke)
        }

        val dotRadius = stroke / 2f + 1.2f
        val dotX = centerX + radius * cos(endAngle)
        val dotY = centerY + radius * sin(endAngle)
        Draw2D.circle(dotX, dotY, dotRadius, NanoVGColor.WHITE)
    }

    fun drawServerIcon(x: Float, y: Float, size: Float, radius: Float, alpha: Float) {
        drawSquircle(x, y, size, size, radius, NanoVGColor(0.12f, 0.13f, 0.18f, 0.85f * alpha))
        drawSquircleOutline(x, y, size, size, radius, NanoVGColor(1f, 1f, 1f, 0.15f * alpha), 1f)

        val barW = 3.5f
        val gap = 3f
        val totalBarsW = barW * 4f + gap * 3f
        val startX = x + (size - totalBarsW) / 2f
        val baseY = y + size - 9f

        val heights = floatArrayOf(5f, 9f, 13f, 17f)
        for (i in 0 until 4) {
            val bx = startX + i * (barW + gap)
            val bh = heights[i]
            val by = baseY - bh
            val barColor = if (i < 3) Theme.getClientColor(i * 3, alpha) else NanoVGColor(1f, 1f, 1f, 0.25f * alpha)
            Draw2D.roundedRect(bx, by, barW, bh, 1.5f, barColor)
        }
    }
}
