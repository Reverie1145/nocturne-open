package cn.reverie.render.ui.components

import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class ExpandablePanel(
    var headerTitle: String,
    var isExpanded: Boolean = true,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    var headerHeight: Float = 32f
    var contentHeight: Float = 100f

    val expandAnim = FloatAnimation(if (isExpanded) 1f else 0f, if (isExpanded) 1f else 0f, 200L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        height = headerHeight + contentHeight * expandAnim.value

        Draw2D.roundedRect(absoluteBounds, 8f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 8f, theme.border)

        val font = RenderEngine.fontRenderer
        font.drawString(headerTitle, absoluteX + 12f, absoluteY + 9f, theme.textPrimary, 13f)

        val chevron = if (isExpanded) IconFont.CHEVRON_UP else IconFont.CHEVRON_DOWN
        font.drawString(chevron, absoluteX + width - 20f, absoluteY + 9f, theme.textSecondary, 11f)

        if (expandAnim.value > 0.05f) {
            super.render(mouseX, mouseY, partialTicks)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (mouseY >= absoluteY && mouseY <= absoluteY + headerHeight && mouseX >= absoluteX && mouseX <= absoluteX + width) {
            isExpanded = !isExpanded
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
