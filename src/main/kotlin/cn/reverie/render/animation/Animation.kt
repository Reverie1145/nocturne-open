package cn.reverie.render.animation

abstract class Animation(
    var durationMs: Long = 300L,
    var easing: (Float) -> Float = Easing.EASE_OUT_CUBIC
) {
    enum class Direction { FORWARDS, BACKWARDS }

    var direction: Direction = Direction.FORWARDS
    var progress: Float = 0f
        protected set

    var startTime: Long = -1L
        protected set
    var isRunning: Boolean = false
        protected set

    var onFinished: (() -> Unit)? = null
    var onUpdate: ((Float) -> Unit)? = null

    fun start(dir: Direction = Direction.FORWARDS) {
        direction = dir
        startTime = System.currentTimeMillis()
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun reset() {
        progress = if (direction == Direction.FORWARDS) 0f else 1f
        isRunning = false
    }

    open fun update() {
        if (!isRunning) return

        val now = System.currentTimeMillis()
        val elapsed = (now - startTime).toFloat()
        val linearProgress = if (durationMs > 0) (elapsed / durationMs.toFloat()).coerceIn(0f, 1f) else 1.0f

        progress = if (direction == Direction.FORWARDS) {
            easing(linearProgress)
        } else {
            1f - easing(linearProgress)
        }

        onUpdate?.invoke(progress)

        if (linearProgress >= 1.0f) {
            progress = if (direction == Direction.FORWARDS) 1.0f else 0.0f
            isRunning = false
            onFinished?.invoke()
        }
    }
}
