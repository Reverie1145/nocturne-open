package cn.reverie.render.animation

import cn.reverie.render.math.Vec2f

class Vec2Animation(
    var startPos: Vec2f,
    var targetPos: Vec2f,
    durationMs: Long = 300L,
    easing: (Float) -> Float = Easing.EASE_OUT_CUBIC
) : Animation(durationMs, easing) {

    val position: Vec2f
        get() = startPos.lerp(targetPos, progress)

    fun animateTo(newTarget: Vec2f) {
        startPos = position
        targetPos = newTarget
        start(Direction.FORWARDS)
    }
}
