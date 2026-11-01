package cn.reverie.render.animation

enum class TransitionType {
    NONE,
    FADE,
    SCALE_UP,
    SCALE_DOWN,
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    POP
}

class Transition(
    val type: TransitionType = TransitionType.SCALE_UP,
    durationMs: Long = 250L
) {
    val animation = FloatAnimation(0f, 1f, durationMs, Easing.EASE_OUT_CUBIC)

    fun open() {
        animation.animateTo(1f)
    }

    fun close(onComplete: () -> Unit = {}) {
        animation.onFinished = onComplete
        animation.animateTo(0f)
    }

    val progress: Float get() = animation.value
    val isOpening: Boolean get() = animation.targetValue == 1f
    val isClosed: Boolean get() = progress == 0f && !animation.isRunning
}
