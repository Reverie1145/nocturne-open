package cn.reverie.render.font

data class FontMetrics(
    val ascent: Float,
    val descent: Float,
    val lineHeight: Float,
    val capHeight: Float = ascent * 0.7f
) {
    val totalHeight: Float get() = ascent - descent
}
