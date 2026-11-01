package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class RadioGroupWidget(
    val options: List<String>,
    var selectedIndex: Int = 0,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = options.size * 22f
    }

    var onSelectionChanged: ((Int) -> Unit)? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        val itemH = 22f

        for (i in options.indices) {
            val itemY = absoluteY + i * itemH
            val isSelected = i == selectedIndex

            val radioX = absoluteX + 8f
            val radioY = itemY + 11f

            Draw2D.circle(radioX, radioY, 6f, theme.surfaceActive)
            Draw2D.circleOutline(radioX, radioY, 6f, if (isSelected) theme.primary else theme.border, 1.5f)

            if (isSelected) {
                Draw2D.circle(radioX, radioY, 3f, theme.primary)
            }

            font.drawString(options[i], radioX + 12f, itemY + 5f, if (isSelected) theme.textPrimary else theme.textSecondary, 12f)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        val itemH = 22f
        for (i in options.indices) {
            val itemY = absoluteY + i * itemH
            if (mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemH) {
                selectedIndex = i
                onSelectionChanged?.invoke(selectedIndex)
                return true
            }
        }
        return false
    }
}
