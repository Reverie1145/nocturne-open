package cn.reverie.render.ui.components
import cn.reverie.render.animation.ColorAnimation
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

open class ButtonWidget(
    var text: String = "Button",
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    var cornerRadius: Float = 6.0f
    var fontSize: Float = 13.0f

    val hoverAnim = FloatAnimation(0f, 1f, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        hoverAnim.animateTo(if (isHovered && isEnabled) 1f else 0f)

        val baseColor = if (!isEnabled) theme.surface else if (isPressed) theme.primaryHover else theme.primary
        val bgColor = baseColor.lerp(theme.primaryHover, hoverAnim.value)

        if (isEnabled && hoverAnim.value > 0.1f) {
            Draw2D.shadow(absoluteX, absoluteY, width, height, cornerRadius, 8f * hoverAnim.value, theme.primary.withAlpha(0.4f * hoverAnim.value))
        }

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, cornerRadius, bgColor)

        val font = RenderEngine.fontRenderer
        val textBlur = if (hoverAnim.isRunning) kotlin.math.sin(hoverAnim.value * Math.PI).toFloat() * 1.2f else 0f
        font.drawCenteredString(
            text,
            absoluteX + width * 0.5f,
            absoluteY + (height - font.getStringHeight(fontSize)) * 0.5f,
            if (isEnabled) theme.textPrimary else theme.textDisabled,
            fontSize,
            shadow = false,
            blur = textBlur
        )
    }
}
