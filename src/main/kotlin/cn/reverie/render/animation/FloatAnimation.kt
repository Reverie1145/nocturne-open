package cn.reverie.render.animation

class FloatAnimation(
    var startValue: Float,
    var targetValue: Float,
    durationMs: Long = 250L,
    easing: (Float) -> Float = Easing.EASE_OUT_CUBIC
) : Animation(durationMs, easing) {

    val value: Float
        get() {
            update()
            return startValue + (targetValue - startValue) * progress
        }

    val isFinished: Boolean
        get() = !isRunning

    fun animateTo(newTarget: Float) {

        if (targetValue == newTarget) {
            if (isRunning) return
            if (kotlin.math.abs(value - newTarget) < 0.001f) return
        }
        startValue = value
        targetValue = newTarget
        progress = 0f
        start(Direction.FORWARDS)
    }

    fun setImmediate(v: Float) {
        startValue = v
        targetValue = v
        progress = 1.0f
        stop()
    }
}
