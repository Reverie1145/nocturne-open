package cn.reverie.gui.mainmenu

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.PI

/**
 * Client login vector icons engine · Zinc Precision v4.0
 */
object LoginVectorIcons {

    fun drawUserIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val context = RenderEngine.nanoVG.handle
        val nvg = RenderEngine.nanoVG
        val scale = size / 24f
        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scale, scale)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        // Head
        path.begin()
        path.circle(0f, -4.5f, 4.2f)
        path.stroke(color, 1.8f)

        // Shoulders
        path.begin()
        path.arc(0f, 9f, 8.5f, (-160.0 * PI / 180.0).toFloat(), (-20.0 * PI / 180.0).toFloat(), NVG_CW)
        path.stroke(color, 1.8f)

        nvg.transform.restore()
    }

    fun drawLockIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val context = RenderEngine.nanoVG.handle
        val nvg = RenderEngine.nanoVG
        val scale = size / 24f
        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scale, scale)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        // Lock body
        path.begin()
        path.roundedRect(-6.5f, -1f, 13f, 10.5f, 2.5f)
        path.stroke(color, 1.7f)

        // Lock shackle
        path.begin()
        path.arc(0f, -2.5f, 4.2f, (-180.0 * PI / 180.0).toFloat(), (0.0 * PI / 180.0).toFloat(), NVG_CW)
        path.stroke(color, 1.7f)

        // Keyhole
        path.begin()
        path.circle(0f, 4f, 1.1f)
        path.fill(color)

        nvg.transform.restore()
    }

    fun drawShieldIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val context = RenderEngine.nanoVG.handle
        val nvg = RenderEngine.nanoVG
        val scale = size / 24f
        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scale, scale)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(0f, -8f)
        path.lineTo(7f, -4.5f)
        path.lineTo(7f, 2f)
        path.bezierTo(7f, 6.5f, 3.5f, 9.5f, 0f, 10.5f)
        path.bezierTo(-3.5f, 9.5f, -7f, 6.5f, -7f, 2f)
        path.lineTo(-7f, -4.5f)
        path.close()
        path.stroke(color, 1.7f)

        nvg.transform.restore()
    }

    fun drawEyeIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor, visible: Boolean) {
        val context = RenderEngine.nanoVG.handle
        val nvg = RenderEngine.nanoVG
        val scale = size / 24f
        nvg.transform.save()
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scale, scale)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val path = NanoVGPath(context)
        // Eye outline
        path.begin()
        path.moveTo(-8.5f, 0f)
        path.bezierTo(-4.5f, -5.5f, 4.5f, -5.5f, 8.5f, 0f)
        path.bezierTo(4.5f, 5.5f, -4.5f, 5.5f, -8.5f, 0f)
        path.close()
        path.stroke(color, 1.6f)

        // Pupil
        path.begin()
        path.circle(0f, 0f, 2.5f)
        if (visible) path.fill(color) else path.stroke(color, 1.4f)

        // Diagonal slash if hidden
        if (!visible) {
            path.begin()
            path.moveTo(-8.5f, -7.5f)
            path.lineTo(8.5f, 7.5f)
            path.stroke(color, 1.6f)
        }

        nvg.transform.restore()
    }

    fun drawSpinner(cx: Float, cy: Float, radius: Float, strokeW: Float, color: NanoVGColor, angle: Float) {
        val context = RenderEngine.nanoVG.handle
        nvgLineCap(context, NVG_ROUND)
        val path = NanoVGPath(context)
        path.begin()
        path.arc(cx, cy, radius, angle, angle + 1.4f * PI.toFloat(), NVG_CW)
        path.stroke(color, strokeW)
    }

    fun drawAlertIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val context = RenderEngine.nanoVG.handle
        val path = NanoVGPath(context)
        path.begin()
        path.circle(cx, cy, size * 0.5f)
        path.stroke(color, 1.2f)

        Draw2D.line(cx, cy - 2.8f, cx, cy + 0.8f, color, 1.3f)
        Draw2D.circle(cx, cy + 2.8f, 0.7f, color)
    }

    fun drawCheckIcon(cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val context = RenderEngine.nanoVG.handle
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(cx - 3.5f, cy)
        path.lineTo(cx - 1f, cy + 3f)
        path.lineTo(cx + 4f, cy - 2.5f)
        path.stroke(color, 1.5f)
    }
}
