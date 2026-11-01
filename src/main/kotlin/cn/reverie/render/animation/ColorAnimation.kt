package cn.reverie.render.animation

import cn.reverie.render.nanovg.NanoVGColor

class ColorAnimation(
    var startColor: NanoVGColor,
    var targetColor: NanoVGColor,
    durationMs: Long = 200L,
    easing: (Float) -> Float = Easing.EASE_OUT_CUBIC
) : Animation(durationMs, easing) {

    val color: NanoVGColor
        get() = startColor.lerp(targetColor, progress)

    fun animateTo(newColor: NanoVGColor) {
        startColor = color
        targetColor = newColor
        start(Direction.FORWARDS)
    }
}
