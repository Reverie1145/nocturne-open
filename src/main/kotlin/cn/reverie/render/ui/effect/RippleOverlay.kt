package cn.reverie.render.ui.effect

import cn.reverie.render.animation.Easing
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.nanovg.NanoVG.nvgIntersectScissor
import org.lwjgl.nanovg.NanoVG.nvgRestore
import org.lwjgl.nanovg.NanoVG.nvgSave
import kotlin.math.max

enum class OverlayShape {
    RECT, ROUNDED_RECT, SQUIRCLE
}

class RippleOverlay {
    var alpha: Float = 1f
    var speed: Float = 1f
    val ripples = ArrayList<Ripple>()
    private val removable = ArrayList<Ripple>()

    fun createRipple(x: Float, y: Float): Ripple = Ripple(x, y)

    fun addRipple(ripple: Ripple) {
        ripples.add(ripple)
    }

    fun render(
        shape: OverlayShape,
        x: Float, y: Float,
        width: Float, height: Float,
        radius: Float,
        mouseX: Int, mouseY: Int,
        partialTicks: Float,
        color: NanoVGColor
    ) {
        if (ripples.isEmpty()) return

        val context = RenderEngine.nanoVG.handle
        nvgSave(context)
        nvgIntersectScissor(context, x, y, width, height)

        removable.clear()
        for (ripple in ripples) {
            ripple.render(context, max(width, height), color, alpha, speed)
            if (ripple.fading && ripple.opacityAni.value <= 0.01f) {
                removable.add(ripple)
            }
        }
        ripples.removeAll(removable)
        nvgRestore(context)
    }

    class Ripple(val x: Float, val y: Float) {
        val opacityAni = FloatAnimation(0f, 1f, 220L, Easing.EASE_OUT_CUBIC)
        val expandAni = FloatAnimation(0f, 1f, 1260L, Easing.EASE_OUT_QUART)
        var fading: Boolean = false

        init {
            opacityAni.animateTo(1f)
            expandAni.animateTo(1f)
        }

        fun render(context: Long, maxDimension: Float, color: NanoVGColor, baseAlpha: Float, speed: Float) {
            if (fading && opacityAni.targetValue != 0f) {
                opacityAni.durationMs = (220L / speed).toLong().coerceAtLeast(50L)
                opacityAni.animateTo(0f)
            }

            val percent = opacityAni.value
            val radius = maxDimension * 2f * expandAni.value

            if (percent > 0.005f && radius > 0.5f) {
                val circleColor = color.multiplyAlpha(percent * 0.22f * baseAlpha)
                Draw2D.circle(x, y, radius, circleColor)
            }
        }
    }
}
