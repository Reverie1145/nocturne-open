package cn.reverie.app.agent.script

import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.system.MemoryStack

/** Called only by the client render event, inside its existing NanoVG frame. */
internal class NanoVGScriptRenderer {
    fun draw(context: Long, frame: ScriptDrawFrame, fonts: Map<String, Int>) {
        require(context != 0L && (fonts["default"] ?: -1) >= 0) { "NanoVG 或默认字体尚未就绪" }
        var depth = 0
        nvgSave(context)
        try {
            // Reset only our saved state, leaving the client's transform, clip, paints and fonts intact.
            nvgReset(context)
            nvgBeginPath(context)
            nvgFontFaceId(context, fonts.getValue("default"))
            nvgFontSize(context, 14f)
            nvgTextAlign(context, NVG_ALIGN_LEFT or NVG_ALIGN_TOP)
            MemoryStack.stackPush().use { stack ->
                val color = NVGColor.malloc(stack)
                val inner = NVGColor.malloc(stack)
                val outer = NVGColor.malloc(stack)
                val paint = NVGPaint.malloc(stack)
                val bounds = stack.mallocFloat(4)
                fun set(target: NVGColor, value: ScriptDrawColor) { target.r(value.r).g(value.g).b(value.b).a(value.a) }
                for (command in frame.commands) {
                    val v = command.numbers
                    when (command.op) {
                        "save" -> { nvgSave(context); depth++ }
                        "restore" -> { nvgRestore(context); depth-- }
                        "resetTransform" -> nvgResetTransform(context)
                        "translate" -> nvgTranslate(context, v[0], v[1])
                        "rotate" -> nvgRotate(context, v[0])
                        "scale" -> nvgScale(context, v[0], v[1])
                        "transform" -> nvgTransform(context, v[0], v[1], v[2], v[3], v[4], v[5])
                        "globalAlpha" -> nvgGlobalAlpha(context, v[0])
                        "scissor" -> nvgScissor(context, v[0], v[1], v[2], v[3])
                        "intersectScissor" -> nvgIntersectScissor(context, v[0], v[1], v[2], v[3])
                        "resetScissor" -> nvgResetScissor(context)
                        "beginPath" -> nvgBeginPath(context)
                        "moveTo" -> nvgMoveTo(context, v[0], v[1])
                        "lineTo" -> nvgLineTo(context, v[0], v[1])
                        "bezierTo" -> nvgBezierTo(context, v[0], v[1], v[2], v[3], v[4], v[5])
                        "quadTo" -> nvgQuadTo(context, v[0], v[1], v[2], v[3])
                        "arcTo" -> nvgArcTo(context, v[0], v[1], v[2], v[3], v[4])
                        "closePath" -> nvgClosePath(context)
                        "pathWinding" -> nvgPathWinding(context, if (v[0] == 0f) NVG_SOLID else NVG_HOLE)
                        "rect" -> nvgRect(context, v[0], v[1], v[2], v[3])
                        "roundedRect" -> nvgRoundedRect(context, v[0], v[1], v[2], v[3], v[4])
                        "roundedRectVarying" -> nvgRoundedRectVarying(context, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])
                        "circle" -> nvgCircle(context, v[0], v[1], v[2])
                        "ellipse" -> nvgEllipse(context, v[0], v[1], v[2], v[3])
                        "arc" -> nvgArc(context, v[0], v[1], v[2], v[3], v[4], if (v[5] == 0f) NVG_CW else NVG_CCW)
                        "fill" -> nvgFill(context)
                        "stroke" -> nvgStroke(context)
                        "fillColor", "strokeColor" -> {
                            set(color, requireNotNull(command.color))
                            if (command.op == "fillColor") nvgFillColor(context, color) else nvgStrokeColor(context, color)
                        }
                        "fillPaint", "strokePaint" -> {
                            val p = requireNotNull(command.paint)
                            val q = p.numbers
                            set(inner, p.inner); set(outer, p.outer)
                            when (p.kind) {
                                "linearGradient" -> nvgLinearGradient(context, q[0], q[1], q[2], q[3], inner, outer, paint)
                                "radialGradient" -> nvgRadialGradient(context, q[0], q[1], q[2], q[3], inner, outer, paint)
                                "boxGradient" -> nvgBoxGradient(context, q[0], q[1], q[2], q[3], q[4], q[5], inner, outer, paint)
                            }
                            if (command.op == "fillPaint") nvgFillPaint(context, paint) else nvgStrokePaint(context, paint)
                        }
                        "strokeWidth" -> nvgStrokeWidth(context, v[0])
                        "miterLimit" -> nvgMiterLimit(context, v[0])
                        "lineCap" -> nvgLineCap(context, intArrayOf(NVG_BUTT, NVG_ROUND, NVG_SQUARE)[v[0].toInt()])
                        "lineJoin" -> nvgLineJoin(context, intArrayOf(NVG_MITER, NVG_ROUND, NVG_BEVEL)[v[0].toInt()])
                        "fontFace" -> nvgFontFaceId(context, fonts[command.text]?.takeIf { it >= 0 } ?: fonts.getValue("default"))
                        "fontSize" -> nvgFontSize(context, v[0])
                        "fontBlur" -> nvgFontBlur(context, v[0])
                        "textLetterSpacing" -> nvgTextLetterSpacing(context, v[0])
                        "textLineHeight" -> nvgTextLineHeight(context, v[0])
                        "textAlign" -> nvgTextAlign(context, intArrayOf(NVG_ALIGN_LEFT, NVG_ALIGN_CENTER, NVG_ALIGN_RIGHT)[v[0].toInt()] or
                            intArrayOf(NVG_ALIGN_TOP, NVG_ALIGN_MIDDLE, NVG_ALIGN_BOTTOM, NVG_ALIGN_BASELINE)[v[1].toInt()])
                        "text" -> nvgText(context, v[0], v[1], command.text)
                        "textEllipsis" -> {
                            fun width(text: String): Float {
                                val advance = nvgTextBounds(context, 0f, 0f, text, bounds)
                                return maxOf(advance, bounds[2] - bounds[0])
                            }
                            val source = command.text
                            val available = v[2]
                            val clipped = if (source.isEmpty() || width(source) <= available) source else {
                                val ellipsis = "…"
                                if (width(ellipsis) > available) "" else {
                                    var low = 0; var high = source.codePointCount(0, source.length)
                                    while (low < high) {
                                        val middle = (low + high + 1) / 2
                                        val candidate = source.substring(0, source.offsetByCodePoints(0, middle)) + ellipsis
                                        if (width(candidate) <= available) low = middle else high = middle - 1
                                    }
                                    source.substring(0, source.offsetByCodePoints(0, low)) + ellipsis
                                }
                            }
                            nvgText(context, v[0], v[1], clipped)
                        }
                        "textBox" -> nvgTextBox(context, v[0], v[1], v[2], command.text)
                        else -> error("未知绘制指令：${command.op}")
                    }
                }
            }
        } finally {
            repeat(depth.coerceAtLeast(0)) { nvgRestore(context) }
            nvgRestore(context)
            // NanoVG's current path isn't part of the save/restore stack.
            nvgBeginPath(context)
        }
    }
}
