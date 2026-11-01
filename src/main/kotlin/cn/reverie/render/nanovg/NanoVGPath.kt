package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack

class NanoVGPath(val context: Long) {

    fun begin(): NanoVGPath {
        nvgBeginPath(context)
        return this
    }

    fun close(): NanoVGPath {
        nvgClosePath(context)
        return this
    }

    fun moveTo(x: Float, y: Float): NanoVGPath {
        nvgMoveTo(context, x, y)
        return this
    }

    fun lineTo(x: Float, y: Float): NanoVGPath {
        nvgLineTo(context, x, y)
        return this
    }

    fun bezierTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float): NanoVGPath {
        nvgBezierTo(context, c1x, c1y, c2x, c2y, x, y)
        return this
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): NanoVGPath {
        nvgQuadTo(context, cx, cy, x, y)
        return this
    }

    fun arcTo(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float): NanoVGPath {
        nvgArcTo(context, x1, y1, x2, y2, radius)
        return this
    }

    fun arc(cx: Float, cy: Float, r: Float, a0: Float, a1: Float, dir: Int = NVG_CW): NanoVGPath {
        nvgArc(context, cx, cy, r, a0, a1, dir)
        return this
    }

    fun rect(x: Float, y: Float, w: Float, h: Float): NanoVGPath {
        nvgRect(context, x, y, w, h)
        return this
    }

    fun roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float): NanoVGPath {
        nvgRoundedRect(context, x, y, w, h, r)
        return this
    }

    fun roundedRectVarying(x: Float, y: Float, w: Float, h: Float, radTopLeft: Float, radTopRight: Float, radBottomRight: Float, radBottomLeft: Float): NanoVGPath {
        nvgRoundedRectVarying(context, x, y, w, h, radTopLeft, radTopRight, radBottomRight, radBottomLeft)
        return this
    }

    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float): NanoVGPath {
        nvgEllipse(context, cx, cy, rx, ry)
        return this
    }

    fun circle(cx: Float, cy: Float, r: Float): NanoVGPath {
        nvgCircle(context, cx, cy, r)
        return this
    }

    fun pathWinding(dir: Int): NanoVGPath {
        nvgPathWinding(context, dir)
        return this
    }

    fun fill(color: NanoVGColor): NanoVGPath {
        MemoryStack.stackPush().use { stack ->
            val c = NVGColor.malloc(stack)
            color.toNVG(c)
            nvgFillColor(context, c)
            nvgFill(context)
        }
        return this
    }

    fun fill(paint: NVGPaint): NanoVGPath {
        nvgFillPaint(context, paint)
        nvgFill(context)
        return this
    }

    fun stroke(color: NanoVGColor, width: Float = 1f): NanoVGPath {
        MemoryStack.stackPush().use { stack ->
            val c = NVGColor.malloc(stack)
            color.toNVG(c)
            nvgStrokeColor(context, c)
            nvgStrokeWidth(context, width)
            nvgStroke(context)
        }
        return this
    }

    fun stroke(paint: NVGPaint, width: Float = 1f): NanoVGPath {
        nvgStrokePaint(context, paint)
        nvgStrokeWidth(context, width)
        nvgStroke(context)
        return this
    }
}
