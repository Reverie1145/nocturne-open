package cn.reverie.render.draw

import cn.reverie.render.math.Vec2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGConstants
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NanoVG.nvgLineCap

object LineRenderer {

    fun drawLine(
        context: Long,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        color: NanoVGColor,
        width: Float = 1.0f,
        cap: Int = NanoVGConstants.CAP_ROUND
    ) {
        nvgLineCap(context, cap)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.stroke(color, width)
    }

    fun drawPolyline(
        context: Long,
        points: List<Vec2f>,
        color: NanoVGColor,
        width: Float = 1.0f,
        closed: Boolean = false
    ) {
        if (points.size < 2) return
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x, points[i].y)
        }
        if (closed) path.close()
        path.stroke(color, width)
    }
}
