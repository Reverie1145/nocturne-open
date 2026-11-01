package cn.reverie.render.ui.components

import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class AvatarWidget(
    var skinTextureId: Int = 0,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 36f
        height = 36f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val radius = 8f
        if (skinTextureId > 0) {

            Draw2D.imageRounded(
                absoluteX, absoluteY, width, height, radius,
                skinTextureId,
                8f / 64f, 8f / 64f, 16f / 64f, 16f / 64f
            )
        } else {
            Draw2D.roundedRect(absoluteBounds, radius, theme.surfaceActive)
        }
        Draw2D.roundedOutline(absoluteBounds, radius, theme.border)
    }
}
