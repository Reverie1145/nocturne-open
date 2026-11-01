package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class DropdownWidget(
    var items: List<String>,
    var selectedIndex: Int = 0,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 140f
        height = 30f
    }

    var isExpanded = false
    var onSelectionChanged: ((Int, String) -> Unit)? = null

    val expandAnim = FloatAnimation(0f, 0f, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        expandAnim.animateTo(if (isExpanded) 1f else 0f)

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 6f, if (isExpanded) theme.borderFocus else theme.border)

        val font = RenderEngine.fontRenderer
        val selectedText = items.getOrElse(selectedIndex) { "Select..." }
        font.drawString(selectedText, absoluteX + 10f, absoluteY + 9f, theme.textPrimary, 13f)

        val chevron = if (isExpanded) IconFont.CHEVRON_UP else IconFont.CHEVRON_DOWN
        font.drawString(chevron, absoluteX + width - 20f, absoluteY + 9f, theme.textSecondary, 11f)

        if (expandAnim.value > 0.05f) {
            val itemH = 26f
            val totalMenuH = items.size * itemH
            val menuY = absoluteY + height + 4f

            Draw2D.shadow(absoluteX, menuY, width, totalMenuH * expandAnim.value, 6f, 12f)
            Draw2D.roundedRect(absoluteX, menuY, width, totalMenuH * expandAnim.value, 6f, theme.surface)
            Draw2D.roundedOutline(absoluteX, menuY, width, totalMenuH * expandAnim.value, 6f, theme.border)

            for (i in items.indices) {
                val itemY = menuY + i * itemH
                val itemHovered = mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemH

                if (itemHovered) {
                    Draw2D.roundedRect(absoluteX + 2f, itemY + 2f, width - 4f, itemH - 4f, 4f, theme.surfaceHover)
                }

                val col = if (i == selectedIndex) theme.primary else if (itemHovered) theme.textPrimary else theme.textSecondary
                font.drawString(items[i], absoluteX + 10f, itemY + 7f, col, 12f)
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false

        if (isExpanded) {
            val itemH = 26f
            val menuY = absoluteY + height + 4f
            for (i in items.indices) {
                val itemY = menuY + i * itemH
                if (mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemH) {
                    selectedIndex = i
                    isExpanded = false
                    onSelectionChanged?.invoke(i, items[i])
                    return true
                }
            }
        }

        if (isMouseOver(mouseX, mouseY)) {
            isExpanded = !isExpanded
            return true
        } else {
            isExpanded = false
        }
        return false
    }
}
