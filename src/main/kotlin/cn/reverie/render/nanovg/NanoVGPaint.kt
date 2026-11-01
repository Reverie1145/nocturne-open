package cn.reverie.render.nanovg

import org.lwjgl.nanovg.NVGColor
import org.lwjgl.nanovg.NVGPaint
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.system.MemoryStack

class NanoVGPaint(val handle: NVGPaint) {

    companion object {
        fun linearGradient(
            context: Long,
            sx: Float, sy: Float,
            ex: Float, ey: Float,
            startColor: NanoVGColor,
            endColor: NanoVGColor,
            dest: NVGPaint
        ): NVGPaint {
            MemoryStack.stackPush().use { stack ->
                val c1 = NVGColor.malloc(stack)
                val c2 = NVGColor.malloc(stack)
                startColor.toNVG(c1)
                endColor.toNVG(c2)
                nvgLinearGradient(context, sx, sy, ex, ey, c1, c2, dest)
            }
            return dest
        }

        fun radialGradient(
            context: Long,
            cx: Float, cy: Float,
            inRadius: Float, outRadius: Float,
            innerColor: NanoVGColor,
            outerColor: NanoVGColor,
            dest: NVGPaint
        ): NVGPaint {
            MemoryStack.stackPush().use { stack ->
                val c1 = NVGColor.malloc(stack)
                val c2 = NVGColor.malloc(stack)
                innerColor.toNVG(c1)
                outerColor.toNVG(c2)
                nvgRadialGradient(context, cx, cy, inRadius, outRadius, c1, c2, dest)
            }
            return dest
        }

        fun boxGradient(
            context: Long,
            x: Float, y: Float,
            w: Float, h: Float,
            radius: Float, feather: Float,
            innerColor: NanoVGColor,
            outerColor: NanoVGColor,
            dest: NVGPaint
        ): NVGPaint {
            MemoryStack.stackPush().use { stack ->
                val c1 = NVGColor.malloc(stack)
                val c2 = NVGColor.malloc(stack)
                innerColor.toNVG(c1)
                outerColor.toNVG(c2)
                nvgBoxGradient(context, x, y, w, h, radius, feather, c1, c2, dest)
            }
            return dest
        }

        fun imagePattern(
            context: Long,
            ox: Float, oy: Float,
            ex: Float, ey: Float,
            angle: Float,
            imageHandle: Int,
            alpha: Float,
            dest: NVGPaint
        ): NVGPaint {
            nvgImagePattern(context, ox, oy, ex, ey, angle, imageHandle, alpha, dest)
            return dest
        }
    }
}
