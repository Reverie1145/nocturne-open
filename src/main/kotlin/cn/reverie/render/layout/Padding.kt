package cn.reverie.render.layout

data class Padding(
    val top: Float,
    val right: Float,
    val bottom: Float,
    val left: Float
) {
    constructor(all: Float) : this(all, all, all, all)
    constructor(horizontal: Float, vertical: Float) : this(vertical, horizontal, vertical, horizontal)

    val horizontal: Float get() = left + right
    val vertical: Float get() = top + bottom

    companion object {
        val ZERO = Padding(0f)
        val SMALL = Padding(4f)
        val MEDIUM = Padding(8f)
        val LARGE = Padding(16f)
    }
}
