package cn.reverie.render.draw

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath

object PatternRenderer {

    fun drawDotGrid(
        context: Long,
        x: Float, y: Float,
        width: Float, height: Float,
        spacing: Float = 16.0f,
        dotRadius: Float = 1.0f,
        color: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.1f)
    ) {
        val path = NanoVGPath(context)
        path.begin()

        var cx = x + spacing * 0.5f
        while (cx < x + width) {
            var cy = y + spacing * 0.5f
            while (cy < y + height) {
                path.circle(cx, cy, dotRadius)
                cy += spacing
            }
            cx += spacing
        }

        path.fill(color)
    }

    fun drawGridLines(
        context: Long,
        x: Float, y: Float,
        width: Float, height: Float,
        spacing: Float = 20.0f,
        color: NanoVGColor = NanoVGColor(1f, 1f, 1f, 0.05f),
        lineWidth: Float = 1.0f
    ) {
        val path = NanoVGPath(context)
        path.begin()

        var cx = x
        while (cx <= x + width) {
            path.moveTo(cx, y)
            path.lineTo(cx, y + height)
            cx += spacing
        }

        var cy = y
        while (cy <= y + height) {
            path.moveTo(x, cy)
            path.lineTo(x + width, cy)
            cy += spacing
        }

        path.stroke(color, lineWidth)
    }
}
