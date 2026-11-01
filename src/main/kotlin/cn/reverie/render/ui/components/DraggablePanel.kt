package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

open class DraggablePanel(
    var title: String,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    var titleBarHeight = 28f
    private var isDragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.shadow(absoluteBounds, 8f, 16f, NanoVGColor(0f, 0f, 0f, 0.4f), 4f)
        Draw2D.roundedRect(absoluteBounds, 8f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 8f, theme.border)

        val font = RenderEngine.fontRenderer
        font.drawString(title, absoluteX + 12f, absoluteY + 8f, theme.textPrimary, 13f)
        Draw2D.line(absoluteX, absoluteY + titleBarHeight, absoluteX + width, absoluteY + titleBarHeight, theme.border, 1.0f)

        super.render(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (mouseY >= absoluteY && mouseY <= absoluteY + titleBarHeight && mouseX >= absoluteX && mouseX <= absoluteX + width) {
            isDragging = true
            dragOffsetX = mouseX - x
            dragOffsetY = mouseY - y
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = isDragging
        isDragging = false
        return was || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDragging) {
            x = mouseX - dragOffsetX
            y = mouseY - dragOffsetY
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }
}
