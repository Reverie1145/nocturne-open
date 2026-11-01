package cn.reverie.render.texture

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

object SvgRenderer {

    sealed class SvgCmd {
        data class MoveTo(val x: Float, val y: Float) : SvgCmd()
        data class LineTo(val x: Float, val y: Float) : SvgCmd()
        data class CubicTo(val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x: Float, val y: Float) : SvgCmd()
        data object Close : SvgCmd()
    }

    private val pathCache = ConcurrentHashMap<String, List<SvgCmd>>()

    fun renderSvg(
        d: String,
        x: Float, y: Float,
        size: Float,
        color: NanoVGColor,
        strokeWidth: Float = 1.35f,
        fill: Boolean = false,
        viewBox: Float = 24.0f,
        context: Long = RenderEngine.nanoVG.handle
    ) {
        val cmds = pathCache.computeIfAbsent(d) { parseSvgPath(it) }
        if (cmds.isEmpty()) return

        val nvg = RenderEngine.nanoVG
        val scale = size / viewBox

        nvg.transform.save()
        nvg.transform.translate(x, y)
        nvg.transform.scale(scale, scale)

        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        nvgBeginPath(context)
        for (cmd in cmds) {
            when (cmd) {
                is SvgCmd.MoveTo -> nvgMoveTo(context, cmd.x, cmd.y)
                is SvgCmd.LineTo -> nvgLineTo(context, cmd.x, cmd.y)
                is SvgCmd.CubicTo -> nvgBezierTo(context, cmd.c1x, cmd.c1y, cmd.c2x, cmd.c2y, cmd.x, cmd.y)
                is SvgCmd.Close -> nvgClosePath(context)
            }
        }

        MemoryStack.stackPush().use { stack ->
            val c = NVGColor.malloc(stack)
            color.toNVG(c)

            if (fill) {
                nvgFillColor(context, c)
                nvgFill(context)
            }
            if (strokeWidth > 0f) {
                nvgStrokeColor(context, c)
                nvgStrokeWidth(context, strokeWidth / scale)
                nvgStroke(context)
            }
        }

        nvg.transform.restore()
    }

    private fun parseSvgPath(d: String): List<SvgCmd> {
        val tokens = tokenize(d)
        if (tokens.isEmpty()) return emptyList()

        val cmds = mutableListOf<SvgCmd>()
        var idx = 0

        var curX = 0f
        var curY = 0f
        var startX = 0f
        var startY = 0f
        var lastCpX = 0f
        var lastCpY = 0f
        var lastCmd = ' '

        fun nextNum(): Float {
            if (idx >= tokens.size) return 0f
            return tokens[idx++].toFloatOrNull() ?: 0f
        }

        fun hasNum(): Boolean {
            if (idx >= tokens.size) return false
            val first = tokens[idx][0]
            return first.isDigit() || first == '-' || first == '+' || first == '.'
        }

        while (idx < tokens.size) {
            val token = tokens[idx++]
            val cmd = token[0]
            val isUpper = cmd.isUpperCase()

            when (cmd) {
                'M', 'm' -> {
                    val x = nextNum()
                    val y = nextNum()
                    if (isUpper) {
                        curX = x
                        curY = y
                    } else {
                        curX += x
                        curY += y
                    }
                    startX = curX
                    startY = curY
                    cmds.add(SvgCmd.MoveTo(curX, curY))

                    while (hasNum()) {
                        val lx = nextNum()
                        val ly = nextNum()
                        if (isUpper) {
                            curX = lx
                            curY = ly
                        } else {
                            curX += lx
                            curY += ly
                        }
                        cmds.add(SvgCmd.LineTo(curX, curY))
                    }
                }
                'L', 'l' -> {
                    while (hasNum()) {
                        val x = nextNum()
                        val y = nextNum()
                        if (isUpper) {
                            curX = x
                            curY = y
                        } else {
                            curX += x
                            curY += y
                        }
                        cmds.add(SvgCmd.LineTo(curX, curY))
                    }
                }
                'H', 'h' -> {
                    while (hasNum()) {
                        val x = nextNum()
                        if (isUpper) curX = x else curX += x
                        cmds.add(SvgCmd.LineTo(curX, curY))
                    }
                }
                'V', 'v' -> {
                    while (hasNum()) {
                        val y = nextNum()
                        if (isUpper) curY = y else curY += y
                        cmds.add(SvgCmd.LineTo(curX, curY))
                    }
                }
                'C', 'c' -> {
                    while (hasNum()) {
                        val c1x = nextNum()
                        val c1y = nextNum()
                        val c2x = nextNum()
                        val c2y = nextNum()
                        val x = nextNum()
                        val y = nextNum()

                        val absC1x = if (isUpper) c1x else curX + c1x
                        val absC1y = if (isUpper) c1y else curY + c1y
                        val absC2x = if (isUpper) c2x else curX + c2x
                        val absC2y = if (isUpper) c2y else curY + c2y
                        if (isUpper) {
                            curX = x
                            curY = y
                        } else {
                            curX += x
                            curY += y
                        }
                        lastCpX = absC2x
                        lastCpY = absC2y
                        cmds.add(SvgCmd.CubicTo(absC1x, absC1y, absC2x, absC2y, curX, curY))
                    }
                }
                'S', 's' -> {
                    while (hasNum()) {
                        val c2x = nextNum()
                        val c2y = nextNum()
                        val x = nextNum()
                        val y = nextNum()

                        val absC1x = if (lastCmd in listOf('C', 'c', 'S', 's')) 2 * curX - lastCpX else curX
                        val absC1y = if (lastCmd in listOf('C', 'c', 'S', 's')) 2 * curY - lastCpY else curY
                        val absC2x = if (isUpper) c2x else curX + c2x
                        val absC2y = if (isUpper) c2y else curY + c2y
                        if (isUpper) {
                            curX = x
                            curY = y
                        } else {
                            curX += x
                            curY += y
                        }
                        lastCpX = absC2x
                        lastCpY = absC2y
                        cmds.add(SvgCmd.CubicTo(absC1x, absC1y, absC2x, absC2y, curX, curY))
                    }
                }
                'Q', 'q' -> {
                    while (hasNum()) {
                        val qx = nextNum()
                        val qy = nextNum()
                        val x = nextNum()
                        val y = nextNum()

                        val absQx = if (isUpper) qx else curX + qx
                        val absQy = if (isUpper) qy else curY + qy
                        val targetX = if (isUpper) x else curX + x
                        val targetY = if (isUpper) y else curY + y

                        val c1x = curX + (2f / 3f) * (absQx - curX)
                        val c1y = curY + (2f / 3f) * (absQy - curY)
                        val c2x = targetX + (2f / 3f) * (absQx - targetX)
                        val c2y = targetY + (2f / 3f) * (absQy - targetY)

                        curX = targetX
                        curY = targetY
                        lastCpX = absQx
                        lastCpY = absQy
                        cmds.add(SvgCmd.CubicTo(c1x, c1y, c2x, c2y, curX, curY))
                    }
                }
                'A', 'a' -> {
                    while (hasNum()) {
                        val rx = nextNum()
                        val ry = nextNum()
                        val rot = nextNum()
                        val largeArc = nextNum() > 0.5f
                        val sweep = nextNum() > 0.5f
                        val x = nextNum()
                        val y = nextNum()
                        val targetX = if (isUpper) x else curX + x
                        val targetY = if (isUpper) y else curY + y

                        arcToBeziers(
                            curX.toDouble(), curY.toDouble(),
                            rx.toDouble(), ry.toDouble(),
                            rot.toDouble(),
                            largeArc, sweep,
                            targetX.toDouble(), targetY.toDouble()
                        ) { c1x, c1y, c2x, c2y, ex, ey ->
                            cmds.add(SvgCmd.CubicTo(c1x.toFloat(), c1y.toFloat(), c2x.toFloat(), c2y.toFloat(), ex.toFloat(), ey.toFloat()))
                        }

                        curX = targetX
                        curY = targetY
                    }
                }
                'Z', 'z' -> {
                    cmds.add(SvgCmd.Close)
                    curX = startX
                    curY = startY
                }
            }
            lastCmd = cmd
        }

        return cmds
    }

    private fun tokenize(d: String): List<String> {
        val tokens = mutableListOf<String>()
        val len = d.length
        var i = 0
        while (i < len) {
            val c = d[i]
            if (c.isWhitespace() || c == ',') {
                i++
                continue
            }
            if (c.isLetter()) {
                tokens.add(c.toString())
                i++
                continue
            }
            val start = i
            if (c == '+' || c == '-') i++
            var hasDot = false
            while (i < len) {
                val ch = d[i]
                if (ch.isDigit()) {
                    i++
                } else if (ch == '.' && !hasDot) {
                    hasDot = true
                    i++
                } else if (ch == 'e' || ch == 'E') {
                    i++
                    if (i < len && (d[i] == '+' || d[i] == '-')) i++
                } else {
                    break
                }
            }
            if (i > start) {
                tokens.add(d.substring(start, i))
            } else {
                i++
            }
        }
        return tokens
    }

    private fun arcToBeziers(
        x1: Double, y1: Double,
        rX: Double, rY: Double,
        angleDeg: Double,
        largeArc: Boolean, sweep: Boolean,
        x2: Double, y2: Double,
        emit: (Double, Double, Double, Double, Double, Double) -> Unit
    ) {
        if (x1 == x2 && y1 == y2) return
        var rx = abs(rX)
        var ry = abs(rY)
        if (rx <= 1e-6 || ry <= 1e-6) {
            emit(x1, y1, x2, y2, x2, y2)
            return
        }

        val phi = Math.toRadians(angleDeg % 360.0)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx = (x1 - x2) / 2.0
        val dy = (y1 - y2) / 2.0
        val x1p = cosPhi * dx + sinPhi * dy
        val y1p = -sinPhi * dx + cosPhi * dy

        val cr = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (cr > 1.0) {
            val s = sqrt(cr)
            rx *= s
            ry *= s
        }

        val sign = if (largeArc == sweep) -1.0 else 1.0
        val num = (rx * rx * ry * ry) - (rx * rx * y1p * y1p) - (ry * ry * x1p * x1p)
        val den = (rx * rx * y1p * y1p) + (ry * ry * x1p * x1p)
        val sq = if (den <= 1e-9) 0.0 else sqrt(max(0.0, num / den))
        val cxp = sign * sq * (rx * y1p / ry)
        val cyp = sign * sq * -(ry * x1p / rx)

        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val signA = if (ux * vy - uy * vx < 0) -1.0 else 1.0
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            if (len <= 1e-9) return 0.0
            val cosA = (dot / len).coerceIn(-1.0, 1.0)
            return signA * acos(cosA)
        }

        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx
        val vy = (-y1p - cyp) / ry

        val theta1 = vectorAngle(1.0, 0.0, ux, uy)
        var dTheta = vectorAngle(ux, uy, vx, vy)
        if (!sweep && dTheta > 0) dTheta -= 2 * Math.PI
        else if (sweep && dTheta < 0) dTheta += 2 * Math.PI

        val segments = ceil(abs(dTheta) / (Math.PI / 2.0)).toInt().coerceAtLeast(1)
        val deltaTheta = dTheta / segments
        val t = 8.0 / 3.0 * sin(deltaTheta / 4.0) * sin(deltaTheta / 4.0) / sin(deltaTheta / 2.0)

        var currentAngle = theta1

        for (i in 0 until segments) {
            val nextAngle = currentAngle + deltaTheta
            val cosA1 = cos(currentAngle)
            val sinA1 = sin(currentAngle)
            val cosA2 = cos(nextAngle)
            val sinA2 = sin(nextAngle)

            val ep1x = cosPhi * rx * cosA1 - sinPhi * ry * sinA1 + cx
            val ep1y = sinPhi * rx * cosA1 + cosPhi * ry * sinA1 + cy
            val ep2x = cosPhi * rx * cosA2 - sinPhi * ry * sinA2 + cx
            val ep2y = sinPhi * rx * cosA2 + cosPhi * ry * sinA2 + cy

            val d1x = -cosPhi * rx * sinA1 - sinPhi * ry * cosA1
            val d1y = -sinPhi * rx * sinA1 + cosPhi * ry * cosA1
            val d2x = -cosPhi * rx * sinA2 - sinPhi * ry * cosA2
            val d2y = -sinPhi * rx * sinA2 + cosPhi * ry * cosA2

            val cp1x = ep1x + t * d1x
            val cp1y = ep1y + t * d1y
            val cp2x = ep2x - t * d2x
            val cp2y = ep2y - t * d2y

            val endX = if (i == segments - 1) x2 else ep2x
            val endY = if (i == segments - 1) y2 else ep2y

            emit(cp1x, cp1y, cp2x, cp2y, endX, endY)
            currentAngle = nextAngle
        }
    }
}

object SvgIcons {

    const val MOON = "M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"

    const val FPS = "M22 12h-4l-3 9L9 3l-3 9H2"

    const val PING = "M5 12.55a11 11 0 0 1 14.08 0 M8.53 16.11a6 6 0 0 1 6.95 0 M12 20a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"

    const val BPS = "M13 2L3 14h9l-1 8 10-12h-9l1-8z"

    const val TIME = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2z M12 6v6l4 2"

    const val SERVER = "M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5z M3 14a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-3z M6 6.5h2 M6 15.5h2"

    const val USER = "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"

    const val BLOCK = "M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z M3.27 6.96L12 12.01l8.73-5.05 M12 22.08V12"
}
