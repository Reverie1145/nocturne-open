package cn.reverie.render.ui.components

import cn.reverie.render.clipping.ClipManager
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class VirtualListWidget<T>(
    val items: List<T>,
    val itemHeight: Float = 26f,
    val renderItem: (T, Float, Float, Float, Float, Boolean) -> Unit,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 180f
        height = 200f
    }

    var scrollY = 0f
    var selectedIndex = -1

    val maxScroll: Float get() = maxOf(0f, items.size * itemHeight - height)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteBounds, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 6f, theme.border)

        val startIndex = (scrollY / itemHeight).toInt().coerceIn(0, items.size)
        val visibleCount = (height / itemHeight).toInt() + 2
        val endIndex = (startIndex + visibleCount).coerceIn(0, items.size)

        ClipManager.clip {
            scissor(absoluteBounds) {
                for (i in startIndex until endIndex) {
                    val itemY = absoluteY + i * itemHeight - scrollY
                    val isHovered = mouseX >= absoluteX && mouseX <= absoluteX + width && mouseY >= itemY && mouseY <= itemY + itemHeight
                    renderItem(items[i], absoluteX, itemY, width, itemHeight, isHovered || i == selectedIndex)
                }
            }
        }
    }

    override fun mouseScrolled(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float): Boolean {
        if (isMouseOver(mouseX, mouseY) && maxScroll > 0f) {
            scrollY = (scrollY - deltaY * 20f).coerceIn(0f, maxScroll)
            return true
        }
        return false
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            val clickedIndex = ((mouseY - absoluteY + scrollY) / itemHeight).toInt()
            if (clickedIndex in items.indices) {
                selectedIndex = clickedIndex
                return true
            }
        }
        return false
    }
}
