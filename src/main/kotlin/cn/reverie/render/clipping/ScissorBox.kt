package cn.reverie.render.clipping

import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.math.Rect2f
import kotlin.math.ceil
import kotlin.math.floor

data class ScissorBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    companion object {
        fun fromGuiRect(rect: Rect2f): ScissorBox {
            val fbW = ResolutionHelper.framebufferWidth.coerceAtLeast(0)
            val fbH = ResolutionHelper.framebufferHeight.coerceAtLeast(0)
            val scaleX = ResolutionHelper.framebufferScaleX
            val scaleY = ResolutionHelper.framebufferScaleY

            val left = floor(minOf(rect.x, rect.x2) * scaleX).toInt().coerceIn(0, fbW)
            val right = ceil(maxOf(rect.x, rect.x2) * scaleX).toInt().coerceIn(0, fbW)
            val top = floor(minOf(rect.y, rect.y2) * scaleY).toInt().coerceIn(0, fbH)
            val bottom = ceil(maxOf(rect.y, rect.y2) * scaleY).toInt().coerceIn(0, fbH)

            return ScissorBox(left, fbH - bottom, right - left, bottom - top)
        }
    }
}
