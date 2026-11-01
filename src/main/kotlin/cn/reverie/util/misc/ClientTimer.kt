package cn.reverie.util.misc

object ClientTimer {
    @Volatile
    private var multiplier = 1.0f

    @JvmStatic
    fun multiplier(): Float = multiplier

    @JvmStatic
    fun setMultiplier(value: Float) {
        multiplier = maxOf(0.05f, value)
    }

    @JvmStatic
    fun reset() {
        multiplier = 1.0f
    }

    @JvmStatic
    fun applyTickTargetMillis(value: Float): Float {
        val current = multiplier
        return if (current == 1.0f) value else value / current
    }
}
