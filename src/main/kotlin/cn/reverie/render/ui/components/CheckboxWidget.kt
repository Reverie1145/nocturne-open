package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class CheckboxWidget(
    var label: String,
    var isChecked: Boolean = false,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 140f
        height = 20f
    }

    var onCheckChanged: ((Boolean) -> Unit)? = null
    val checkAnim = FloatAnimation(if (isChecked) 1f else 0f, if (isChecked) 1f else 0f, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        checkAnim.animateTo(if (isChecked) 1f else 0f)

        val boxSize = 16f
        val boxY = absoluteY + (height - boxSize) * 0.5f

        val boxColor = theme.surfaceActive.lerp(theme.primary, checkAnim.value)
        Draw2D.roundedRect(absoluteX, boxY, boxSize, boxSize, 4f, boxColor)
        Draw2D.roundedOutline(absoluteX, boxY, boxSize, boxSize, 4f, theme.border)

        if (checkAnim.value > 0.05f) {
            val font = RenderEngine.fontRenderer
            font.drawCenteredString(
                IconFont.CHECK,
                absoluteX + boxSize * 0.5f,
                boxY + 2f,
                NanoVGColor.WHITE.withAlpha(checkAnim.value),
                10f
            )
        }

        val font = RenderEngine.fontRenderer
        font.drawString(
            label,
            absoluteX + boxSize + 8f,
            absoluteY + 3f,
            if (isEnabled) theme.textPrimary else theme.textDisabled,
            13f
        )
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            isChecked = !isChecked
            onCheckChanged?.invoke(isChecked)
            return true
        }
        return false
    }
}
