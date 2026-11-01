package cn.reverie.render.ui.components
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class TooltipWidget(
    var title: String,
    var description: String? = null,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val font = RenderEngine.fontRenderer
        val titleW = font.getStringWidth(title, 12f)
        val descW = description?.let { font.getStringWidth(it, 11f) } ?: 0f
        val tooltipW = maxOf(titleW, descW) + 16f
        val tooltipH = if (description != null) 36f else 22f

        val tx = mouseX + 12f
        val ty = mouseY + 12f

        Draw2D.shadow(tx, ty, tooltipW, tooltipH, 6f, 10f, NanoVGColor(0f, 0f, 0f, 0.4f))
        Draw2D.roundedRect(tx, ty, tooltipW, tooltipH, 6f, theme.surface)
        Draw2D.roundedOutline(tx, ty, tooltipW, tooltipH, 6f, theme.border)

        font.drawString(title, tx + 8f, ty + 5f, theme.textPrimary, 12f)
        if (description != null) {
            font.drawString(description!!, tx + 8f, ty + 19f, theme.textSecondary, 11f)
        }
    }
}
