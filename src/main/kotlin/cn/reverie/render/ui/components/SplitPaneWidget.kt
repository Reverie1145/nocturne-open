package cn.reverie.render.ui.components

import cn.reverie.render.draw.Draw2D
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class SplitPaneWidget(
    val leftPane: UIElement,
    val rightPane: UIElement,
    var splitRatio: Float = 0.5f,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    private var isDraggingDivider = false

    init {
        add(leftPane)
        add(rightPane)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val dividerW = 4f
        val leftW = (width - dividerW) * splitRatio
        val rightW = width - dividerW - leftW

        leftPane.x = 0f
        leftPane.y = 0f
        leftPane.width = leftW
        leftPane.height = height

        rightPane.x = leftW + dividerW
        rightPane.y = 0f
        rightPane.width = rightW
        rightPane.height = height

        val dividerX = absoluteX + leftW

        Draw2D.rect(dividerX, absoluteY, dividerW, height, if (isDraggingDivider) theme.primary else theme.border)

        super.render(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        val dividerW = 6f
        val leftW = (width - dividerW) * splitRatio
        val dividerX = absoluteX + leftW

        if (mouseX >= dividerX - 2f && mouseX <= dividerX + dividerW + 2f && isMouseOver(mouseX, mouseY)) {
            isDraggingDivider = true
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = isDraggingDivider
        isDraggingDivider = false
        return was || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDraggingDivider) {
            splitRatio = ((mouseX - absoluteX) / width).coerceIn(0.15f, 0.85f)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
    }
}
