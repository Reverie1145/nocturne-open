package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGColor

sealed class FontEffect {
    object None : FontEffect()

    data class DropShadow(
        val offsetX: Float = 1.0f,
        val offsetY: Float = 1.0f,
        val color: NanoVGColor = NanoVGColor(0f, 0f, 0f, 0.6f),
        val blur: Float = 0.0f
    ) : FontEffect()

    data class OuterGlow(
        val color: NanoVGColor,
        val radius: Float = 3.0f
    ) : FontEffect()

    data class GradientFill(
        val topColor: NanoVGColor,
        val bottomColor: NanoVGColor
    ) : FontEffect()

    data class Rainbow(
        val speed: Float = 1.0f,
        val spread: Float = 20.0f
    ) : FontEffect()

    data class Outline(
        val color: NanoVGColor = NanoVGColor.BLACK,
        val width: Float = 1.0f
    ) : FontEffect()
}
