package cn.reverie.render.shader.blur

object KawaseOffset {

    val DEFAULT_OFFSETS = floatArrayOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f)

    fun getOffsetsForIterations(iterations: Int): FloatArray {
        val count = iterations.coerceIn(1, 8)
        return FloatArray(count) { i -> 1.0f + (i * 0.5f) }
    }
}
