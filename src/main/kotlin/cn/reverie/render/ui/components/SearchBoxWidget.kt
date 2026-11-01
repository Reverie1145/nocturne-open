package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.ui.UITheme

class SearchBoxWidget(
    theme: UITheme = UITheme.DARK
) : TextInputWidget(placeholder = "Search modules...", theme = theme) {

    init {
        width = 160f
        height = 28f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 6f, if (isFocused) theme.borderFocus else theme.border)

        val font = RenderEngine.fontRenderer

        font.drawString(IconFont.SEARCH, absoluteX + 8f, absoluteY + 8f, theme.textSecondary, 12f)

        if (text.isEmpty() && !isFocused) {
            font.drawString(placeholder, absoluteX + 26f, absoluteY + 7f, theme.textDisabled, 12f)
        } else {
            font.drawString(text, absoluteX + 26f, absoluteY + 7f, theme.textPrimary, 12f)
        }
    }
}
