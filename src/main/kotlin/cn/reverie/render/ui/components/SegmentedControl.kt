package cn.reverie.render.ui.components

import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class SegmentedControl(
    val segments: List<String>,
    var selectedIndex: Int = 0,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 28f
    }

    var onSelectionChanged: ((Int) -> Unit)? = null
    private val indicatorAnim = FloatAnimation(0f, 0f, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible || segments.isEmpty()) return

        val segWidth = width / segments.size
        indicatorAnim.animateTo(selectedIndex * segWidth)

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 6f, theme.border)

        Draw2D.roundedRect(absoluteX + indicatorAnim.value + 2f, absoluteY + 2f, segWidth - 4f, height - 4f, 4f, theme.primary)

        val font = RenderEngine.fontRenderer
        for (i in segments.indices) {
            val segX = absoluteX + i * segWidth
            val isSelected = i == selectedIndex
            font.drawCenteredString(
                segments[i],
                segX + segWidth * 0.5f,
                absoluteY + 7f,
                if (isSelected) NanoVGColor.WHITE else theme.textSecondary,
                11f
            )
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            val segWidth = width / segments.size
            val clicked = ((mouseX - absoluteX) / segWidth).toInt().coerceIn(0, segments.size - 1)
            if (selectedIndex != clicked) {
                selectedIndex = clicked
                onSelectionChanged?.invoke(selectedIndex)
            }
            return true
        }
        return false
    }
}
