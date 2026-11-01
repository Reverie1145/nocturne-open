package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ToggleSwitch(
    var label: String,
    var isToggled: Boolean = false,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 24f
    }

    var onToggled: ((Boolean) -> Unit)? = null
    val toggleAnim = FloatAnimation(if (isToggled) 1f else 0f, if (isToggled) 1f else 0f, 180L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        toggleAnim.animateTo(if (isToggled) 1f else 0f)

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY + 4f, if (isEnabled) theme.textPrimary else theme.textDisabled, 13f)

        val switchW = 38f
        val switchH = 20f
        val switchX = absoluteX + width - switchW
        val switchY = absoluteY + (height - switchH) * 0.5f
        val radius = switchH * 0.5f

        val bgColor = theme.surfaceActive.lerp(theme.primary, toggleAnim.value)
        Draw2D.roundedRect(switchX, switchY, switchW, switchH, radius, bgColor)

        val knobRadius = 7f
        val minKnobX = switchX + knobRadius + 3f
        val maxKnobX = switchX + switchW - knobRadius - 3f
        val knobX = minKnobX + (maxKnobX - minKnobX) * toggleAnim.value
        val knobY = switchY + switchH * 0.5f

        Draw2D.circle(knobX, knobY, knobRadius, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            isToggled = !isToggled
            onToggled?.invoke(isToggled)
            return true
        }
        return false
    }
}
