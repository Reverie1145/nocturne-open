package cn.reverie.gui.clickgui

import cn.reverie.render.math.Rect2f
import kotlin.math.sin

/** The text clip and clear button never share space, even while the field expands. */
internal class ClickGuiSearchFieldLayout(
    val bounds: Rect2f,
    textInset: Float = 31f,
    clearRightInset: Float = 8f
) {
    val clearButton = Rect2f(bounds.x2 - clearRightInset - 18f, bounds.centerY - 9f, 18f, 18f)
    val text = Rect2f(bounds.x + textInset, bounds.y + 2f,
        (clearButton.x - 4f - bounds.x - textInset).coerceAtLeast(0f), bounds.height - 4f)
}

/** One rendered layout supplies both drawing and input coordinates. */
internal class ClickGuiCommandBarLayout(
    screenWidth: Float,
    val progress: Float,
    closing: Boolean,
    val focus: Float,
    val statusText: String,
    statusWidth: Float
) {
    private val width = maxOf(430f + 12f * focus, statusWidth + 345f)
    private val y = when {
        closing -> -55f + 67f * progress
        progress < 0.65f -> {
            val t = (progress / 0.65f).coerceIn(0f, 1f)
            -55f + 72f * t * t * (3f - 2f * t)
        }
        progress < 0.85f -> 17f - 6f * sin(((progress - 0.65f) / 0.20f) * Math.PI * 0.5).toFloat()
        else -> 11f + sin(((progress - 0.85f) / 0.15f) * Math.PI * 0.5).toFloat()
    }
    val bounds = Rect2f((screenWidth - width) * 0.5f, y, width, 26f)
    val reset = Rect2f(bounds.x, y, 80f, bounds.height)
    val status = Rect2f(bounds.x2 - statusWidth - 12f, y, statusWidth, bounds.height)
    val hud = Rect2f(status.x - 48f, bounds.centerY - 8f, 40f, 16f)
    val config = Rect2f(hud.x - 46f, hud.y, 40f, 16f)
    val irc = Rect2f(config.x - 46f, hud.y, 40f, 16f)
    val search = ClickGuiSearchFieldLayout(
        Rect2f(reset.x2, y, irc.x - 6f - reset.x2, bounds.height), textInset = 25f, clearRightInset = 0f
    )

    private val scale = when {
        closing || progress >= 0.999f -> Pair(1f, 1f)
        progress < 0.65f -> {
            val stretch = sin(progress / 0.65f * Math.PI).toFloat()
            Pair(1f - 0.14f * stretch, 1f + 0.20f * stretch)
        }
        progress < 0.85f -> {
            val squash = sin((progress - 0.65f) / 0.20f * Math.PI).toFloat()
            Pair(1f + 0.12f * squash, 1f - 0.15f * squash)
        }
        else -> {
            val rebound = sin((progress - 0.85f) / 0.15f * Math.PI).toFloat() * 0.03f
            Pair(1f - rebound, 1f + rebound)
        }
    }
    val scaleX: Float get() = scale.first
    val scaleY: Float get() = scale.second

    fun pointer(mx: Float, my: Float): Pair<Float, Float> = Pair(
        bounds.centerX + (mx - bounds.centerX) / scaleX,
        bounds.centerY + (my - bounds.centerY) / scaleY
    )
}
