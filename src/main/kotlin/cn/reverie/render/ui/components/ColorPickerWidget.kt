package cn.reverie.render.ui.components
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ColorPickerWidget(
    selectedColor: NanoVGColor = NanoVGColor.RED,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    private enum class DragTarget { SV, HUE, ALPHA }

    private data class PickerGeometry(
        val svX: Float,
        val svY: Float,
        val svW: Float,
        val svH: Float,
        val hueY: Float,
        val hueH: Float,
        val alphaY: Float,
        val alphaH: Float
    )

    init {
        width = 160f
        height = 148f
    }

    var hue: Float = 0f
        private set
    var saturation: Float = 1f
        private set
    var value: Float = 1f
        private set
    var alpha: Float = 1f
        private set

    var selectedColor: NanoVGColor = selectedColor
        set(color) {
            field = color
            syncComponentsFromColor(color)
        }

    var onColorChanged: ((NanoVGColor) -> Unit)? = null

    private var dragTarget: DragTarget? = null

    init {
        syncComponentsFromColor(selectedColor)
    }

    private fun geometry(): PickerGeometry {
        val svX = absoluteX + 10f
        val svY = absoluteY + 10f
        val svW = (width - 20f).coerceAtLeast(1f)
        val svH = (height - 68f).coerceAtLeast(44f)
        val hueY = svY + svH + 8f
        val hueH = 9f
        val alphaY = hueY + hueH + 6f
        val alphaH = 7f
        return PickerGeometry(svX, svY, svW, svH, hueY, hueH, alphaY, alphaH)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, 8f, theme.surface)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 8f, theme.border)

        val g = geometry()
        val pureHueColor = NanoVGColor.fromHsv(hue, 1f, 1f)
        NanoVGRenderer.drawRoundedRect(g.svX, g.svY, g.svW, g.svH, 4f, pureHueColor)
        NanoVGRenderer.drawGradientRoundedRect(
            g.svX, g.svY, g.svW, g.svH, 4f,
            NanoVGColor.WHITE, NanoVGColor.WHITE.withAlpha(0f), horizontal = true
        )
        NanoVGRenderer.drawGradientRoundedRect(
            g.svX, g.svY, g.svW, g.svH, 4f,
            NanoVGColor.BLACK.withAlpha(0f), NanoVGColor.BLACK, horizontal = false
        )
        NanoVGRenderer.drawRoundedOutline(g.svX, g.svY, g.svW, g.svH, 4f, NanoVGColor(1f, 1f, 1f, 0.16f), 0.75f)

        val cursorX = (g.svX + saturation * g.svW).coerceIn(g.svX + 4f, g.svX + g.svW - 4f)
        val cursorY = (g.svY + (1f - value) * g.svH).coerceIn(g.svY + 4f, g.svY + g.svH - 4f)
        NanoVGRenderer.drawCircle(cursorX, cursorY, 4.5f, NanoVGColor(0f, 0f, 0f, 0.42f))
        NanoVGRenderer.drawCircle(cursorX, cursorY, 3.6f, NanoVGColor.WHITE)
        NanoVGRenderer.drawCircle(cursorX, cursorY, 2.0f, selectedColor.withAlpha(1f))

        renderHueBar(g)
        renderAlphaBar(g)
        NanoVGRenderer.drawCircle(absoluteX + width - 19f, absoluteY + height - 13f, 8f, selectedColor)
        NanoVGRenderer.drawCircleOutline(absoluteX + width - 19f, absoluteY + height - 13f, 8f, NanoVGColor(1f, 1f, 1f, 0.65f), 1f)
    }

    private fun renderHueBar(g: PickerGeometry) {
        val colors = arrayOf(
            NanoVGColor(1f, 0f, 0f), NanoVGColor(1f, 1f, 0f),
            NanoVGColor(0f, 1f, 0f), NanoVGColor(0f, 1f, 1f),
            NanoVGColor(0f, 0f, 1f), NanoVGColor(1f, 0f, 1f), NanoVGColor(1f, 0f, 0f)
        )
        val segmentW = g.svW / (colors.size - 1).toFloat()
        for (i in 0 until colors.lastIndex) {
            NanoVGRenderer.drawGradientRoundedRect(
                g.svX + i * segmentW, g.hueY, segmentW + 0.5f, g.hueH, 0f,
                colors[i], colors[i + 1], horizontal = true
            )
        }
        NanoVGRenderer.drawRoundedOutline(g.svX, g.hueY, g.svW, g.hueH, g.hueH * 0.5f, NanoVGColor(1f, 1f, 1f, 0.18f), 0.75f)

        val thumbX = (g.svX + hue / 360f * g.svW).coerceIn(g.svX + g.hueH * 0.5f, g.svX + g.svW - g.hueH * 0.5f)
        NanoVGRenderer.drawCircle(thumbX, g.hueY + g.hueH * 0.5f, g.hueH * 0.60f, NanoVGColor(0f, 0f, 0f, 0.42f))
        NanoVGRenderer.drawCircle(thumbX, g.hueY + g.hueH * 0.5f, g.hueH * 0.43f, NanoVGColor.WHITE)
    }

    private fun renderAlphaBar(g: PickerGeometry) {
        val fullColor = NanoVGColor.fromHsv(hue, saturation, value, 1f)
        NanoVGRenderer.drawRoundedRect(g.svX, g.alphaY, g.svW, g.alphaH, g.alphaH * 0.5f, theme.surfaceActive)
        NanoVGRenderer.drawGradientRoundedRect(
            g.svX, g.alphaY, g.svW, g.alphaH, g.alphaH * 0.5f,
            fullColor.withAlpha(0f), fullColor, horizontal = true
        )
        NanoVGRenderer.drawRoundedOutline(g.svX, g.alphaY, g.svW, g.alphaH, g.alphaH * 0.5f, NanoVGColor(1f, 1f, 1f, 0.16f), 0.75f)

        val thumbX = (g.svX + alpha * g.svW).coerceIn(g.svX + g.alphaH * 0.5f, g.svX + g.svW - g.alphaH * 0.5f)
        NanoVGRenderer.drawCircle(thumbX, g.alphaY + g.alphaH * 0.5f, g.alphaH * 0.62f, NanoVGColor(0f, 0f, 0f, 0.42f))
        NanoVGRenderer.drawCircle(thumbX, g.alphaY + g.alphaH * 0.5f, g.alphaH * 0.42f, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled || button != 0 || !isMouseOver(mouseX, mouseY)) return false

        val g = geometry()
        val target = when {
            contains(mouseX, mouseY, g.svX, g.svY, g.svW, g.svH) -> DragTarget.SV
            contains(mouseX, mouseY, g.svX, g.hueY, g.svW, g.hueH) -> DragTarget.HUE
            contains(mouseX, mouseY, g.svX, g.alphaY, g.svW, g.alphaH) -> DragTarget.ALPHA
            else -> null
        }
        dragTarget = target
        if (target != null) {
            isPressed = true
            isFocused = true
            onClick?.invoke()
            updateFromMouse(mouseX, mouseY, g, target)
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        val target = dragTarget ?: return false
        if (button != 0 || !isEnabled || !isVisible) return false
        updateFromMouse(mouseX, mouseY, geometry(), target)
        return true
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        dragTarget = null
        return super.mouseReleased(mouseX, mouseY, button)
    }

    private fun updateFromMouse(mouseX: Float, mouseY: Float, g: PickerGeometry, target: DragTarget) {
        when (target) {
            DragTarget.SV -> {
                saturation = ((mouseX - g.svX) / g.svW).coerceIn(0f, 1f)
                value = (1f - (mouseY - g.svY) / g.svH).coerceIn(0f, 1f)
            }
            DragTarget.HUE -> hue = (((mouseX - g.svX) / g.svW) * 360f).coerceIn(0f, 360f)
            DragTarget.ALPHA -> alpha = ((mouseX - g.svX) / g.svW).coerceIn(0f, 1f)
        }
        selectedColor = NanoVGColor.fromHsv(hue, saturation, value, alpha)
        onColorChanged?.invoke(selectedColor)
    }

    private fun contains(mouseX: Float, mouseY: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
        mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h

    private fun syncComponentsFromColor(color: NanoVGColor) {
        val hsv = color.toHsv()
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        alpha = hsv[3]
    }
}
