package cn.reverie.render.animation

class Timeline {

    data class Keyframe(val timeMs: Long, val value: Float, val easing: (Float) -> Float = Easing.LINEAR)

    private val keyframes = ArrayList<Keyframe>()
    var durationMs: Long = 0L
        private set

    fun addKeyframe(timeMs: Long, value: Float, easing: (Float) -> Float = Easing.LINEAR): Timeline {
        keyframes.add(Keyframe(timeMs, value, easing))
        keyframes.sortBy { it.timeMs }
        durationMs = keyframes.lastOrNull()?.timeMs ?: 0L
        return this
    }

    fun evaluate(currentTimeMs: Long): Float {
        if (keyframes.isEmpty()) return 0f
        if (currentTimeMs <= keyframes.first().timeMs) return keyframes.first().value
        if (currentTimeMs >= keyframes.last().timeMs) return keyframes.last().value

        for (i in 0 until keyframes.size - 1) {
            val k1 = keyframes[i]
            val k2 = keyframes[i + 1]
            if (currentTimeMs in k1.timeMs..k2.timeMs) {
                val t = (currentTimeMs - k1.timeMs).toFloat() / (k2.timeMs - k1.timeMs).toFloat()
                val easedT = k2.easing(t)
                return k1.value + (k2.value - k1.value) * easedT
            }
        }
        return keyframes.last().value
    }
}
