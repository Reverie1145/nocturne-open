package cn.reverie.gui.clickgui

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG.nvgTransform

/** Moves the complete glass surface and shades its inset backing. */
internal class ClickGuiPressFeedback {
    private val motion = ClickGuiSurfaceMotion()
    val compression: Float get() = motion.compression

    fun press(x: Float, y: Float, surfaceX: Float, surfaceY: Float, width: Float, height: Float, button: Int) {
        if (width <= 0f || height <= 0f) return
        motion.press(x - surfaceX, y - surfaceY, width, height, button)
    }

    fun release(button: Int) = motion.release(button)

    fun releaseAll() = motion.releaseAll()

    fun reset() {
        motion.reset()
    }

    fun pointer(mx: Float, my: Float, x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> =
        motion.pose(x, y, width, height).inversePoint(mx, my)

    fun delta(dx: Float, dy: Float, x: Float, y: Float, width: Float, height: Float): Pair<Float, Float> =
        motion.pose(x, y, width, height).inverseDelta(dx, dy)

    fun withSurface(x: Float, y: Float, width: Float, height: Float, radius: Float, alpha: Float, draw: () -> Unit) {
        motion.advance()
        val pose = motion.pose(x, y, width, height)
        val context = RenderEngine.nanoVG.handle
        val transform = RenderEngine.nanoVG.transform

        if (compression > 0.005f) {
            // The stationary backing becomes visible as the front is pushed inward.
            NanoVGRenderer.drawRoundedRect(x, y + 2f, width, height, radius,
                NanoVGColor(0.012f, 0.015f, 0.025f, 0.24f * compression * alpha))
            NanoVGRenderer.drawRoundedOutline(x, y + 2f, width, height, radius,
                NanoVGColor(0f, 0f, 0f, 0.24f * compression * alpha), 1.2f)
        }

        transform.save()
        try {
            nvgTransform(context, pose.a, pose.b, pose.c, pose.d, pose.e, pose.f)
            draw()
            if (compression > 0.005f) {
                NanoVGRenderer.drawGradientRoundedRect(x, y, width, height, radius,
                    NanoVGColor(1f, 1f, 1f, 0.025f * compression * alpha),
                    NanoVGColor(0f, 0f, 0f, 0.075f * compression * alpha))
            }
            NanoVGRenderer.drawRoundedOutline(x + 0.5f, y + 0.5f, width - 1f, height - 1f,
                (radius - 0.5f).coerceAtLeast(0f),
                NanoVGColor(1f, 1f, 1f, (0.055f + 0.065f * compression) * alpha), 0.8f)
        } finally {
            transform.restore()
        }
    }
}
