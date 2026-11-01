package cn.reverie.gui.clickgui

import cn.reverie.module.Category
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.cos
import kotlin.math.sin

object CategoryVectorIcons {

    private val context: Long get() = RenderEngine.nanoVG.handle

    fun drawAllIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor, activity: Float = 0f) {
        val time = System.currentTimeMillis() * 0.004f
        val base = size * 0.25f
        val spacing = size * 0.34f
        for (row in 0..1) {
            for (column in 0..1) {
                val index = row * 2 + column
                val wave = (kotlin.math.sin(time + index * 0.85f) + 1f) * 0.5f
                val scale = 0.88f + wave * (0.10f + activity * 0.10f)
                val cell = base * scale
                val x = cx + (column - 0.5f) * spacing - cell * 0.5f
                val y = cy + (row - 0.5f) * spacing - cell * 0.5f
                NanoVGRenderer.drawRoundedRect(x, y, cell, cell, cell * 0.28f,
                    color.withAlpha(color.a * (0.68f + wave * 0.32f)))
            }
        }
    }

    fun drawCategoryIcon(category: Category, x: Float, y: Float, size: Float, color: NanoVGColor) {
        val img = NanoVGRenderer.loadImage(category.iconName)
        if (img != null && img.isValid) {
            NanoVGRenderer.drawImage(category.iconName, x, y, size, size, alpha = color.a, tintColor = NanoVGColor.WHITE)
            return
        }

        val cx = x + size * 0.5f
        val cy = y + size * 0.5f

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        when (category) {
            Category.COMBAT -> drawCombat(cx, cy, size, color)
            Category.MOVEMENT -> drawMovement(cx, cy, size, color)
            Category.RENDER -> drawRender(cx, cy, size, color)
            Category.PLAYER -> drawPlayer(cx, cy, size, color)
            Category.WORLD -> drawWorld(cx, cy, size, color)
            Category.MISC -> drawMisc(cx, cy, size, color)
        }
    }

    private inline fun renderIcon24(
        cx: Float, cy: Float, size: Float,
        block: (NanoVGPath) -> Unit
    ) {
        val nvg = RenderEngine.nanoVG
        val scale = size / 24.0f
        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scale, scale)

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        block(path)

        nvg.transform.restore()
    }

    fun drawCombat(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f

            path.begin()
            path.moveTo(-8.5f, -8.5f)
            path.lineTo(4.0f, 4.0f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(-6.2f, -9.5f)
            path.lineTo(-8.5f, -8.5f)
            path.lineTo(-9.5f, -6.2f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(1.5f, 6.5f)
            path.lineTo(6.5f, 1.5f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(4.0f, 4.0f)
            path.lineTo(7.8f, 7.8f)
            path.stroke(color, strokeW)
            path.begin()
            path.circle(8.5f, 8.5f, 1.2f)
            path.fill(color)

            path.begin()
            path.moveTo(8.5f, -8.5f)
            path.lineTo(-4.0f, 4.0f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(6.2f, -9.5f)
            path.lineTo(8.5f, -8.5f)
            path.lineTo(9.5f, -6.2f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(-1.5f, 6.5f)
            path.lineTo(-6.5f, 1.5f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(-4.0f, 4.0f)
            path.lineTo(-7.8f, 7.8f)
            path.stroke(color, strokeW)
            path.begin()
            path.circle(-8.5f, 8.5f, 1.2f)
            path.fill(color)
        }
    }

    fun drawMovement(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f

            path.begin()
            path.circle(2.0f, -6.2f, 2.2f)
            path.fill(color)

            path.begin()
            path.arc(-2.2f, 2.5f, 5.5f, 3.8f, 1.9f, NVG_CW)
            path.stroke(color, strokeW)

            path.begin()
            path.circle(-2.2f, 2.5f, 1.0f)
            path.fill(color)

            path.begin()
            path.moveTo(1.0f, -3.2f)
            path.lineTo(-1.6f, 1.2f)
            path.lineTo(3.6f, 1.2f)
            path.lineTo(5.8f, 6.5f)
            path.lineTo(8.0f, 6.5f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(1.0f, -2.0f)
            path.lineTo(3.2f, -0.2f)
            path.lineTo(1.8f, 2.5f)
            path.stroke(color, 1.6f)

            path.begin()
            path.circle(6.8f, 8.0f, 1.2f)
            path.fill(color)
        }
    }

    fun drawRender(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f
            val rx = 9.8f
            val ry = 5.6f

            path.begin()
            path.moveTo(-rx, 0f)
            path.bezierTo(-rx * 0.52f, -ry, rx * 0.52f, -ry, rx, 0f)
            path.bezierTo(rx * 0.52f, ry, -rx * 0.52f, ry, -rx, 0f)
            path.stroke(color, strokeW)

            path.begin()
            path.circle(0f, 0f, 3.4f)
            path.stroke(color, 1.6f)

            path.begin()
            path.circle(0f, 0f, 1.5f)
            path.fill(color)
        }
    }

    fun drawPlayer(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f

            path.begin()
            path.circle(0f, -4.5f, 3.8f)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(-7.5f, 8.8f)
            path.bezierTo(-7.5f, 3.8f, -4.0f, 1.8f, 0f, 1.8f)
            path.bezierTo(4.0f, 1.8f, 7.5f, 3.8f, 7.5f, 8.8f)
            path.stroke(color, strokeW)
        }
    }

    fun drawWorld(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f
            val r = 9.2f

            path.begin()
            path.circle(0f, 0f, r)
            path.stroke(color, strokeW)

            path.begin()
            path.moveTo(-r, 0f)
            path.lineTo(r, 0f)
            path.stroke(color, 1.6f)

            path.begin()
            path.ellipse(0f, 0f, 4.4f, r)
            path.stroke(color, 1.6f)
        }
    }

    fun drawMisc(cx: Float, cy: Float, s: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, s) { path ->
            val strokeW = 1.85f
            val numTeeth = 6
            val rOut = 9.2f
            val rIn = 6.4f

            path.begin()
            for (i in 0 until numTeeth) {
                val baseA = (i * Math.PI / 3.0).toFloat()
                val a0 = baseA - 0.24f
                val a1 = baseA - 0.15f
                val a2 = baseA + 0.15f
                val a3 = baseA + 0.24f
                val aNext = ((i + 1) * Math.PI / 3.0).toFloat() - 0.24f

                val cos0 = cos(a0); val sin0 = sin(a0)
                val cos1 = cos(a1); val sin1 = sin(a1)
                val cos2 = cos(a2); val sin2 = sin(a2)
                val cos3 = cos(a3); val sin3 = sin(a3)
                val cosN = cos(aNext); val sinN = sin(aNext)

                if (i == 0) {
                    path.moveTo(rIn * cos0, rIn * sin0)
                } else {
                    path.lineTo(rIn * cos0, rIn * sin0)
                }
                path.lineTo(rOut * cos1, rOut * sin1)
                path.lineTo(rOut * cos2, rOut * sin2)
                path.lineTo(rIn * cos3, rIn * sin3)
                path.lineTo(rIn * cosN, rIn * sinN)
            }
            path.close()
            path.stroke(color, strokeW)

            path.begin()
            path.circle(0f, 0f, 2.8f)
            path.stroke(color, 1.7f)
        }
    }

    fun drawChatIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, size) { path ->
            val w = 8.6f
            val h = 6.2f
            val r = 2.4f

            path.begin()
            path.moveTo(-w + r, -h)
            path.lineTo(w - r, -h)
            path.bezierTo(w, -h, w, -h, w, -h + r)
            path.lineTo(w, h - r)
            path.bezierTo(w, h, w, h, w - r, h)
            path.lineTo(-1.4f, h)
            path.lineTo(-4.6f, h + 3.4f)
            path.lineTo(-3.4f, h)
            path.lineTo(-w + r, h)
            path.bezierTo(-w, h, -w, h, -w, h - r)
            path.lineTo(-w, -h + r)
            path.bezierTo(-w, -h, -w, -h, -w + r, -h)
            path.stroke(color, 1.7f)

            path.begin()
            path.circle(-3.8f, 0f, 1.05f)
            path.fill(color)

            path.begin()
            path.circle(0f, 0f, 1.05f)
            path.fill(color)

            path.begin()
            path.circle(3.8f, 0f, 1.05f)
            path.fill(color)
        }
    }

    fun drawChevronDown(cx: Float, cy: Float, size: Float, rotationProgress: Float, color: NanoVGColor) {
        val angle = (Math.PI * rotationProgress).toFloat()
        val nvg = RenderEngine.nanoVG

        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.rotate(angle)
        nvg.transform.translate(-cx, -cy)

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        val halfW = size * 0.5f
        val halfH = size * 0.28f

        path.begin()
        path.moveTo(cx - halfW, cy - halfH)
        path.lineTo(cx, cy + halfH)
        path.lineTo(cx + halfW, cy - halfH)
        path.stroke(color, 1.25f)

        nvg.transform.restore()
    }

    fun drawChevronRight(cx: Float, cy: Float, size: Float, expandProgress: Float, color: NanoVGColor) {
        val angle = (Math.PI * 0.5f * expandProgress).toFloat()
        val nvg = RenderEngine.nanoVG

        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.rotate(angle)
        nvg.transform.translate(-cx, -cy)

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        val halfH = size * 0.45f
        val halfW = size * 0.26f

        path.begin()
        path.moveTo(cx - halfW, cy - halfH)
        path.lineTo(cx + halfW, cy)
        path.lineTo(cx - halfW, cy + halfH)
        path.stroke(color, 1.25f)

        nvg.transform.restore()
    }

    fun drawChevron(x: Float, y: Float, width: Float, height: Float, rotationProgress: Float, color: NanoVGColor) {
        drawChevronDown(x + width * 0.5f, y + height * 0.5f, maxOf(width, height), rotationProgress, color)
    }

    fun drawSearchIcon(x: Float, y: Float, size: Float, color: NanoVGColor) {
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        val r = size * 0.32f
        val cx = x + r + size * 0.08f
        val cy = y + r + size * 0.08f

        path.begin()
        path.circle(cx, cy, r)
        path.stroke(color, 1.2f)

        path.begin()
        path.moveTo(cx + r * 0.707f, cy + r * 0.707f)
        path.lineTo(x + size - size * 0.06f, y + size - size * 0.06f)
        path.stroke(color, 1.3f)
    }

    fun drawClearIcon(x: Float, y: Float, size: Float, color: NanoVGColor) {
        nvgLineCap(context, NVG_ROUND)
        val path = NanoVGPath(context)
        val pad = size * 0.22f
        path.begin()
        path.moveTo(x + pad, y + pad)
        path.lineTo(x + size - pad, y + size - pad)
        path.moveTo(x + size - pad, y + pad)
        path.lineTo(x + pad, y + size - pad)
        path.stroke(color, 1.2f)
    }

    fun drawCheckmark(x: Float, y: Float, size: Float, color: NanoVGColor) {
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(x + size * 0.20f, y + size * 0.50f)
        path.lineTo(x + size * 0.45f, y + size * 0.75f)
        path.lineTo(x + size * 0.85f, y + size * 0.25f)
        path.stroke(color, 1.3f)
    }

    fun drawConfigIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        renderIcon24(cx, cy, size) { path ->
            val strokeW = 1.6f
            val halfW = 8.0f

            // Top track & knob
            path.begin()
            path.moveTo(-halfW, -5.2f)
            path.lineTo(halfW, -5.2f)
            path.stroke(color.withAlpha(color.a * 0.70f), strokeW)

            path.begin()
            path.circle(-2.6f, -5.2f, 2.3f)
            path.fill(color)

            // Middle track & knob
            path.begin()
            path.moveTo(-halfW, 0f)
            path.lineTo(halfW, 0f)
            path.stroke(color.withAlpha(color.a * 0.70f), strokeW)

            path.begin()
            path.circle(3.4f, 0f, 2.3f)
            path.fill(color)

            // Bottom track & knob
            path.begin()
            path.moveTo(-halfW, 5.2f)
            path.lineTo(halfW, 5.2f)
            path.stroke(color.withAlpha(color.a * 0.70f), strokeW)

            path.begin()
            path.circle(-4.2f, 5.2f, 2.3f)
            path.fill(color)
        }
    }
}
