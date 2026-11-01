package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ChipWidget(
    var text: String,
    var isSelected: Boolean = false,
    var isRemovable: Boolean = false,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        height = 24f
    }

    var onRemoved: (() -> Unit)? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        val textW = font.getStringWidth(text, 11f)
        width = textW + (if (isRemovable) 32f else 18f)

        val bg = if (isSelected) theme.primary else theme.surface
        val radius = height * 0.5f

        Draw2D.roundedRect(absoluteBounds, radius, bg)
        Draw2D.roundedOutline(absoluteBounds, radius, if (isSelected) theme.borderFocus else theme.border)

        font.drawString(text, absoluteX + 9f, absoluteY + 6f, if (isSelected) cn.reverie.render.nanovg.NanoVGColor.WHITE else theme.textPrimary, 11f)

        if (isRemovable) {
            font.drawString(IconFont.CLOSE, absoluteX + width - 14f, absoluteY + 7f, theme.textSecondary, 10f)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            if (isRemovable && mouseX >= absoluteX + width - 18f) {
                onRemoved?.invoke()
                return true
            }
            isSelected = !isSelected
            onClick?.invoke()
            return true
        }
        return false
    }
}
