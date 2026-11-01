package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class TabPanel(
    val tabNames: List<String>,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    var selectedIndex = 0
    val indicatorAnim = FloatAnimation(0f, 0f, 180L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val tabWidth = width / tabNames.size
        val tabHeight = 32f

        indicatorAnim.animateTo(selectedIndex * tabWidth)

        Draw2D.roundedRect(absoluteX, absoluteY, width, tabHeight, 6f, theme.surface)

        Draw2D.roundedRect(absoluteX + indicatorAnim.value + 3f, absoluteY + 3f, tabWidth - 6f, tabHeight - 6f, 4f, theme.primary)

        val font = RenderEngine.fontRenderer
        for (i in tabNames.indices) {
            val tabX = absoluteX + i * tabWidth
            val isSelected = i == selectedIndex
            font.drawCenteredString(
                tabNames[i],
                tabX + tabWidth * 0.5f,
                absoluteY + 10f,
                if (isSelected) NanoVGColor.WHITE else theme.textSecondary,
                13f
            )
        }

        super.render(mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (mouseY >= absoluteY && mouseY <= absoluteY + 32f) {
            val tabWidth = width / tabNames.size
            val clickedIndex = ((mouseX - absoluteX) / tabWidth).toInt().coerceIn(0, tabNames.size - 1)
            selectedIndex = clickedIndex
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
