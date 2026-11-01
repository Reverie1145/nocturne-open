package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.GradientRenderer
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ColorSlider(
    var label: String,
    var value: Float = 1.0f,
    val startColor: NanoVGColor,
    val endColor: NanoVGColor,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 140f
        height = 24f
    }

    var onValueChanged: ((Float) -> Unit)? = null
    private var isDragging = false

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY, theme.textSecondary, 11f)

        val trackY = absoluteY + 14f
        val trackH = 8f
        val radius = trackH * 0.5f

        GradientRenderer.drawLinearRounded(
            RenderEngine.nanoVG.handle,
            absoluteX, trackY, width, trackH,
            CornerRadii(radius),
            startColor, endColor,
            horizontal = true
        )

        Draw2D.roundedOutline(absoluteX, trackY, width, trackH, radius, theme.border)

        val thumbX = absoluteX + width * value.coerceIn(0f, 1f)
        val thumbY = trackY + trackH * 0.5f
        Draw2D.circle(thumbX, thumbY, 6f, NanoVGColor.WHITE)
        Draw2D.circleOutline(thumbX, thumbY, 6f, theme.surfaceActive, 1.5f)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            isDragging = true
            updateValue(mouseX)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = isDragging
        isDragging = false
        return was
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDragging) {
            updateValue(mouseX)
            return true
        }
        return false
    }

    private fun updateValue(mouseX: Float) {
        val t = ((mouseX - absoluteX) / width).coerceIn(0f, 1f)
        if (value != t) {
            value = t
            onValueChanged?.invoke(value)
        }
    }
}
