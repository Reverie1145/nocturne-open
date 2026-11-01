package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.math.MathUtils
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class RangeSliderWidget(
    var label: String,
    var lowerValue: Float = 20f,
    var upperValue: Float = 80f,
    var minValue: Float = 0f,
    var maxValue: Float = 100f,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 36f
    }

    var onRangeChanged: ((Float, Float) -> Unit)? = null
    private var draggingThumb: Int = 0

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY, theme.textSecondary, 12f)

        val rangeText = "${lowerValue.toInt()} - ${upperValue.toInt()}"
        font.drawString(rangeText, absoluteX + width - font.getStringWidth(rangeText, 11f), absoluteY, theme.textPrimary, 11f)

        val trackY = absoluteY + 20f
        val trackH = 4f
        Draw2D.roundedRect(absoluteX, trackY, width, trackH, 2f, theme.surfaceActive)

        val normLower = ((lowerValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        val normUpper = ((upperValue - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

        val fillX = absoluteX + width * normLower
        val fillW = width * (normUpper - normLower)
        Draw2D.roundedRect(fillX, trackY, fillW, trackH, 2f, theme.primary)

        val thumbY = trackY + trackH * 0.5f
        Draw2D.circle(fillX, thumbY, 5f, NanoVGColor.WHITE)
        Draw2D.circle(fillX + fillW, thumbY, 5f, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            val t = ((mouseX - absoluteX) / width).coerceIn(0f, 1f)
            val clickedVal = minValue + t * (maxValue - minValue)

            draggingThumb = if (Math.abs(clickedVal - lowerValue) < Math.abs(clickedVal - upperValue)) 1 else 2
            updateDrag(mouseX)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = draggingThumb != 0
        draggingThumb = 0
        return was
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (draggingThumb != 0) {
            updateDrag(mouseX)
            return true
        }
        return false
    }

    private fun updateDrag(mouseX: Float) {
        val t = ((mouseX - absoluteX) / width).coerceIn(0f, 1f)
        val v = minValue + t * (maxValue - minValue)

        if (draggingThumb == 1) {
            lowerValue = v.coerceIn(minValue, upperValue)
        } else if (draggingThumb == 2) {
            upperValue = v.coerceIn(lowerValue, maxValue)
        }
        onRangeChanged?.invoke(lowerValue, upperValue)
    }
}
