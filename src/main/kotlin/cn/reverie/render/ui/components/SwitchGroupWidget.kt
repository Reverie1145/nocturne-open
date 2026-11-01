package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class SwitchGroupWidget(
    val title: String,
    val description: String,
    var isToggled: Boolean = false,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    val toggle = ToggleSwitch("", isToggled, theme)

    init {
        width = 240f
        height = 44f
        toggle.width = 40f
        toggle.height = 20f
        add(toggle)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteBounds, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 6f, theme.border)

        val font = RenderEngine.fontRenderer
        font.drawString(title, absoluteX + 10f, absoluteY + 8f, theme.textPrimary, 13f)
        font.drawString(description, absoluteX + 10f, absoluteY + 24f, theme.textSecondary, 11f)

        toggle.x = width - 50f
        toggle.y = (height - toggle.height) * 0.5f

        super.render(mouseX, mouseY, partialTicks)
    }
}
