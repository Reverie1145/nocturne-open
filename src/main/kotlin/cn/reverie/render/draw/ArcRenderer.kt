package cn.reverie.render.draw

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGConstants
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NanoVG.nvgLineCap

object ArcRenderer {

    fun drawArc(
        context: Long,
        cx: Float, cy: Float,
        radius: Float,
        startAngleRad: Float,
        endAngleRad: Float,
        color: NanoVGColor,
        strokeWidth: Float = 2.0f,
        roundedCaps: Boolean = true
    ) {
        nvgLineCap(context, if (roundedCaps) NanoVGConstants.CAP_ROUND else NanoVGConstants.CAP_BUTT)
        val path = NanoVGPath(context)
        path.begin()
        path.arc(cx, cy, radius, startAngleRad, endAngleRad, NanoVGConstants.CW)
        path.stroke(color, strokeWidth)
    }

    fun drawProgressRing(
        context: Long,
        cx: Float, cy: Float,
        radius: Float,
        progress: Float,
        trackColor: NanoVGColor,
        fillColor: NanoVGColor,
        strokeWidth: Float = 3.0f
    ) {
        val startAngle = -Math.PI.toFloat() * 0.5f
        val sweep = Math.PI.toFloat() * 2.0f * progress.coerceIn(0f, 1f)

        drawArc(context, cx, cy, radius, 0f, Math.PI.toFloat() * 2f, trackColor, strokeWidth, false)

        if (progress > 0.001f) {
            drawArc(context, cx, cy, radius, startAngle, startAngle + sweep, fillColor, strokeWidth, true)
        }
    }
}
