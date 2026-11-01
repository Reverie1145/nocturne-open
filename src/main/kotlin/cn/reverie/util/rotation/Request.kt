package cn.reverie.util.rotation

class Request(
    val rotation: Rotation,
    val priority: Priority,
    val movementFix: MoveFix,
    val isSmooth: Boolean,
    val smoothMode: SmoothMode,
    val rotationSpeed: Float,
    var ticksRemaining: Int
) {
    fun decrement() {
        ticksRemaining--
    }

    val isExpired: Boolean
        get() = ticksRemaining < 0
}
