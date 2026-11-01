package cn.reverie.render.draw

import cn.reverie.render.math.Vec4f

data class CornerRadii(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
) {
    constructor(all: Float) : this(all, all, all, all)
    constructor(top: Float, bottom: Float) : this(top, top, bottom, bottom)

    fun toVec4f(): Vec4f = Vec4f(topLeft, topRight, bottomRight, bottomLeft)

    val isUniform: Boolean get() = topLeft == topRight && topRight == bottomRight && bottomRight == bottomLeft

    companion object {
        val ZERO = CornerRadii(0f)
        val SMALL = CornerRadii(4f)
        val MEDIUM = CornerRadii(8f)
        val LARGE = CornerRadii(16f)
        val PILL = CornerRadii(9999f)
    }
}
