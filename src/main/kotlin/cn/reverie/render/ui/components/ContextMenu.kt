package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ContextMenu(
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    data class Item(val label: String, val icon: String? = null, val action: () -> Unit)

    val items = ArrayList<Item>()
    var isOpen = false
    val openAnim = FloatAnimation(0f, 0f, 150L)

    init {
        width = 140f
    }

    fun addItem(label: String, icon: String? = null, action: () -> Unit): ContextMenu {
        items.add(Item(label, icon, action))
        height = items.size * 26f + 8f
        return this
    }

    fun showAt(mouseX: Float, mouseY: Float) {
        x = mouseX
        y = mouseY
        isOpen = true
        isVisible = true
    }

    fun hide() {
        isOpen = false
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        openAnim.animateTo(if (isOpen) 1f else 0f)
        if (openAnim.value <= 0.01f) {
            isVisible = false
            return
        }

        Draw2D.shadow(absoluteBounds, 6f, 16f, NanoVGColor(0f, 0f, 0f, 0.4f * openAnim.value), 4f)
        Draw2D.roundedRect(absoluteBounds, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 6f, theme.border)

        val font = RenderEngine.fontRenderer
        val itemH = 26f

        for (i in items.indices) {
            val item = items[i]
            val itemY = absoluteY + 4f + i * itemH
            val isItemHovered = mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemH

            if (isItemHovered) {
                Draw2D.roundedRect(absoluteX + 3f, itemY, width - 6f, itemH, 4f, theme.surfaceHover)
            }

            if (item.icon != null) {
                font.drawString(item.icon, absoluteX + 8f, itemY + 7f, theme.primary, 11f)
            }

            val textX = if (item.icon != null) absoluteX + 24f else absoluteX + 10f
            font.drawString(item.label, textX, itemY + 7f, if (isItemHovered) theme.textPrimary else theme.textSecondary, 12f)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isOpen) return false
        val itemH = 26f
        for (i in items.indices) {
            val itemY = absoluteY + 4f + i * itemH
            if (mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemH) {
                items[i].action()
                hide()
                return true
            }
        }
        hide()
        return false
    }
}
