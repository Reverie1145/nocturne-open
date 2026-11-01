package cn.reverie.render.ui.components
import cn.reverie.render.clipping.ClipManager
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class ScrollPanel(
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    var scrollY: Float = 0f
    var maxScrollY: Float = 0f
    private var isDraggingScrollBar = false

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        var totalChildHeight = 0f
        for (child in children) {
            totalChildHeight = maxOf(totalChildHeight, child.y + child.height)
        }
        maxScrollY = maxOf(0f, totalChildHeight - height + padding.vertical)

        ClipManager.clip {
            scissor(absoluteBounds) {
                for (child in children) {
                    if (child.isVisible) {
                        val originalY = child.y
                        child.y -= scrollY
                        child.isHovered = child.isMouseOver(mouseX, mouseY)
                        child.render(mouseX, mouseY, partialTicks)
                        child.y = originalY
                    }
                }
            }
        }

        if (maxScrollY > 0f) {
            val scrollBarW = 4f
            val scrollBarH = maxOf(20f, (height / totalChildHeight) * height)
            val scrollBarX = absoluteX + width - scrollBarW - 2f
            val scrollBarY = absoluteY + (scrollY / maxScrollY) * (height - scrollBarH)

            Draw2D.roundedRect(scrollBarX, scrollBarY, scrollBarW, scrollBarH, 2f, theme.surfaceActive)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled || !isMouseOver(mouseX, mouseY)) return false
        for (child in children.reversed()) {
            val origY = child.y
            child.y -= scrollY
            val handled = child.mouseClicked(mouseX, mouseY, button)
            child.y = origY
            if (handled) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        var handled = false
        for (child in children.reversed()) {
            val origY = child.y
            child.y -= scrollY
            if (child.mouseReleased(mouseX, mouseY, button)) handled = true
            child.y = origY
        }
        return handled || super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Float, mouseY: Float, deltaX: Float, deltaY: Float): Boolean {
        if (isMouseOver(mouseX, mouseY) && maxScrollY > 0f) {
            scrollY = (scrollY - deltaY * 20f).coerceIn(0f, maxScrollY)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }
}
