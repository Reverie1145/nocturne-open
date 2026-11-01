package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.PolygonRenderer
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class RatingWidget(
    var maxStars: Int = 5,
    var rating: Int = 4,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = maxStars * 20f
        height = 20f
    }

    var onRatingChanged: ((Int) -> Unit)? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val starSize = 14f
        val gap = 6f

        for (i in 1..maxStars) {
            val sx = absoluteX + (i - 1) * (starSize + gap) + starSize * 0.5f
            val sy = absoluteY + height * 0.5f

            val color = if (i <= rating) theme.warning else theme.surfaceActive
            PolygonRenderer.drawRegularPolygon(
                RenderEngine.nanoVG.handle,
                sx, sy,
                starSize * 0.5f,
                5,
                color,
                -Math.PI.toFloat() * 0.5f
            )
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            val starSize = 14f
            val gap = 6f
            val clicked = ((mouseX - absoluteX) / (starSize + gap)).toInt() + 1
            rating = clicked.coerceIn(1, maxStars)
            onRatingChanged?.invoke(rating)
            return true
        }
        return false
    }
}
