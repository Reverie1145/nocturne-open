package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.ArcRenderer
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class LoadingSpinnerWidget(
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 24f
        height = 24f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val cx = absoluteX + width * 0.5f
        val cy = absoluteY + height * 0.5f
        val radius = width * 0.4f

        val time = (System.currentTimeMillis() % 1000) / 1000f
        val startAngle = time * Math.PI.toFloat() * 2f
        val sweep = Math.PI.toFloat() * 1.2f

        ArcRenderer.drawArc(
            RenderEngine.nanoVG.handle,
            cx, cy, radius,
            startAngle, startAngle + sweep,
            theme.primary,
            2.5f,
            true
        )
    }
}
