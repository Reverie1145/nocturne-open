package cn.reverie.gui.mainmenu

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.RoundedRect
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPaint
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pointer hover highlight and rounded rectangle laser glow engine · Zinc Precision v4.0
 */
object LaserGlowHelper {

    private val laserP0 = FloatArray(2)
    private val laserP1 = FloatArray(2)

    fun drawPointerHighlight(
        x: Float, y: Float, w: Float, h: Float, radius: Float,
        mouseX: Float, mouseY: Float, intensity: Float
    ) {
        val context = RenderEngine.nanoVG.handle
        if (context == 0L || intensity <= 0.005f) return
        MemoryStack.stackPush().use { stack ->
            val paint = NVGPaint.malloc(stack)
            NanoVGPaint.radialGradient(
                context, mouseX, mouseY, 2f, 70f,
                NanoVGColor(1f, 1f, 1f, 0.14f * intensity),
                NanoVGColor(1f, 1f, 1f, 0f),
                paint
            )
            RoundedRect.draw(context, x, y, w, h, CornerRadii(radius), paint)
        }
    }

    fun renderPerimeterLaserBeam(
        context: Long,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float, phase: Float, beamLength: Float,
        alpha: Float, isDanger: Boolean = false
    ) {
        if (alpha <= 0.005f) return
        val straightW = (w - 2f * radius).coerceAtLeast(0f)
        val straightH = (h - 2f * radius).coerceAtLeast(0f)
        val arcLen = (0.5 * PI * radius).toFloat()
        val totalPerimeter = 2f * straightW + 2f * straightH + 4f * arcLen
        if (totalPerimeter <= 0f) return

        val samples = 12
        nvgLineCap(context, NVG_ROUND)

        for (i in 0 until samples) {
            val t0 = i / samples.toFloat()
            val t1 = (i + 1) / samples.toFloat()
            val d0 = phase - beamLength * (1f - t0)
            val d1 = phase - beamLength * (1f - t1)

            getRoundedRectPerimeterPoint(x, y, w, h, radius, d0, totalPerimeter, straightW, straightH, arcLen, laserP0)
            getRoundedRectPerimeterPoint(x, y, w, h, radius, d1, totalPerimeter, straightW, straightH, arcLen, laserP1)

            val segAlpha = (t1 * t1 * alpha).coerceIn(0f, 1f)
            val strokeW = 1.0f + 0.6f * t1
            val segColor = if (isDanger) {
                NanoVGColor(1.0f, 0.40f + 0.50f * t1, 0.40f + 0.50f * t1, segAlpha)
            } else {
                NanoVGColor(0.85f + 0.15f * t1, 0.92f + 0.08f * t1, 1.0f, segAlpha)
            }

            val path = NanoVGPath(context)
            path.begin()
            path.moveTo(laserP0[0], laserP0[1])
            path.lineTo(laserP1[0], laserP1[1])
            path.stroke(segColor, strokeW)
        }
    }

    private fun getRoundedRectPerimeterPoint(
        x: Float, y: Float, w: Float, h: Float, r: Float,
        d: Float, totalPerimeter: Float, straightW: Float, straightH: Float, arcLen: Float,
        out: FloatArray
    ) {
        val dist = ((d % totalPerimeter) + totalPerimeter) % totalPerimeter
        val b0 = straightW
        val b1 = b0 + arcLen
        val b2 = b1 + straightH
        val b3 = b2 + arcLen
        val b4 = b3 + straightW
        val b5 = b4 + arcLen
        val b6 = b5 + straightH

        when {
            dist < b0 -> {
                out[0] = x + r + dist
                out[1] = y
            }
            dist < b1 -> {
                val rem = dist - b0
                val angle = -PI / 2.0 + (rem / arcLen) * (PI / 2.0)
                out[0] = (x + w - r) + cos(angle).toFloat() * r
                out[1] = (y + r) + sin(angle).toFloat() * r
            }
            dist < b2 -> {
                val rem = dist - b1
                out[0] = x + w
                out[1] = y + r + rem
            }
            dist < b3 -> {
                val rem = dist - b2
                val angle = (rem / arcLen) * (PI / 2.0)
                out[0] = (x + w - r) + cos(angle).toFloat() * r
                out[1] = (y + h - r) + sin(angle).toFloat() * r
            }
            dist < b4 -> {
                val rem = dist - b3
                out[0] = x + w - r - rem
                out[1] = y + h
            }
            dist < b5 -> {
                val rem = dist - b4
                val angle = PI / 2.0 + (rem / arcLen) * (PI / 2.0)
                out[0] = (x + r) + cos(angle).toFloat() * r
                out[1] = (y + h - r) + sin(angle).toFloat() * r
            }
            dist < b6 -> {
                val rem = dist - b5
                out[0] = x
                out[1] = y + h - r - rem
            }
            else -> {
                val rem = dist - b6
                val angle = PI + (rem / arcLen) * (PI / 2.0)
                out[0] = (x + r) + cos(angle).toFloat() * r
                out[1] = (y + r) + sin(angle).toFloat() * r
            }
        }
    }
}
