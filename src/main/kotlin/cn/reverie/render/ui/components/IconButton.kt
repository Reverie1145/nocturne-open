package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class IconButton(
    var icon: String = IconFont.COG,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 28f
        height = 28f
    }

    val hoverAnim = FloatAnimation(0f, 1f, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        hoverAnim.animateTo(if (isHovered && isEnabled) 1f else 0f)

        val bgColor = theme.surface.lerp(theme.surfaceHover, hoverAnim.value)
        val radius = width * 0.5f

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, radius, bgColor)
        if (hoverAnim.value > 0.05f) {
            Draw2D.roundedOutline(absoluteX, absoluteY, width, height, radius, theme.borderFocus.withAlpha(hoverAnim.value))
        }

        val font = RenderEngine.fontRenderer
        font.drawCenteredString(
            icon,
            absoluteX + width * 0.5f,
            absoluteY + (height - font.getStringHeight(12f)) * 0.5f,
            if (isEnabled) theme.textPrimary else theme.textDisabled,
            12f
        )
    }
}
