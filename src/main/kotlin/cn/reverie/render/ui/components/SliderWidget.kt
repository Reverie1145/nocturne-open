package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.math.MathUtils
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class SliderWidget(
    var label: String,
    var value: Float,
    var minValue: Float = 0f,
    var maxValue: Float = 100f,
    var step: Float = 1f,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    var onValueChanged: ((Float) -> Unit)? = null
    private var dragging = false

    private val normValue: Float
        get() = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

    private val thumbAnim = FloatAnimation(normValue, normValue, 100L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        thumbAnim.animateTo(normValue)

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY, theme.textSecondary, 12f)

        val valStr = MathUtils.roundToDecimal(value, 1).toString()
        font.drawString(valStr, absoluteX + width - font.getStringWidth(valStr, 12f), absoluteY, theme.textPrimary, 12f)

        val trackY = absoluteY + 18f
        val trackH = 4f

        Draw2D.roundedRect(absoluteX, trackY, width, trackH, 2f, theme.surfaceActive)

        val fillWidth = width * thumbAnim.value
        Draw2D.roundedRect(absoluteX, trackY, fillWidth, trackH, 2f, theme.primary)

        val thumbX = absoluteX + fillWidth
        val thumbY = trackY + trackH * 0.5f
        Draw2D.circle(thumbX, thumbY, 6f, theme.primaryHover)
        Draw2D.circle(thumbX, thumbY, 3f, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            dragging = true
            updateValueFromMouse(mouseX)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val wasDragging = dragging
        dragging = false
        return wasDragging
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (dragging) {
            updateValueFromMouse(mouseX)
            return true
        }
        return false
    }

    private fun updateValueFromMouse(mouseX: Float) {
        val t = ((mouseX - absoluteX) / width).coerceIn(0f, 1f)
        val raw = minValue + t * (maxValue - minValue)
        val stepped = MathUtils.clamp(Math.round(raw / step) * step, minValue, maxValue)
        if (value != stepped) {
            value = stepped
            onValueChanged?.invoke(value)
        }
    }
}
