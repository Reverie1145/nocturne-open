package cn.reverie.render.ui.components
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class CardWidget(
    var title: String = "",
    var elevation: Int = 2,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.shadow(absoluteBounds, 8f, elevation * 6f, NanoVGColor(0f, 0f, 0f, 0.3f), elevation * 2f)
        Draw2D.roundedRect(absoluteBounds, 8f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 8f, theme.border)

        if (title.isNotEmpty()) {
            val font = RenderEngine.fontRenderer
            font.drawString(title, absoluteX + 12f, absoluteY + 12f, theme.textPrimary, 14f)
            Draw2D.line(absoluteX + 12f, absoluteY + 32f, absoluteX + width - 12f, absoluteY + 32f, theme.border, 1.0f)
        }

        super.render(mouseX, mouseY, partialTicks)
    }
}
