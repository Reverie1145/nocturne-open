package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.IconFont
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class NumericStepperWidget(
    var label: String,
    var value: Int = 10,
    var min: Int = 0,
    var max: Int = 100,
    var step: Int = 1,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 26f
    }

    var onValueChanged: ((Int) -> Unit)? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY + 6f, theme.textSecondary, 12f)

        val btnW = 20f
        val btnH = 20f
        val startBtnsX = absoluteX + width - (btnW * 2f + 36f)
        val btnY = absoluteY + (height - btnH) * 0.5f

        Draw2D.roundedRect(startBtnsX, btnY, btnW, btnH, 4f, theme.surfaceActive)
        font.drawCenteredString(IconFont.MINUS, startBtnsX + btnW * 0.5f, btnY + 4f, theme.textPrimary, 10f)

        val valStr = value.toString()
        font.drawCenteredString(valStr, startBtnsX + btnW + 18f, btnY + 4f, theme.textPrimary, 12f)

        val plusX = startBtnsX + btnW + 36f
        Draw2D.roundedRect(plusX, btnY, btnW, btnH, 4f, theme.surfaceActive)
        font.drawCenteredString(IconFont.PLUS, plusX + btnW * 0.5f, btnY + 4f, theme.textPrimary, 10f)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false

        val btnW = 20f
        val btnH = 20f
        val startBtnsX = absoluteX + width - (btnW * 2f + 36f)
        val btnY = absoluteY + (height - btnH) * 0.5f
        val plusX = startBtnsX + btnW + 36f

        if (mouseX >= startBtnsX && mouseX <= startBtnsX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            value = (value - step).coerceIn(min, max)
            onValueChanged?.invoke(value)
            return true
        }

        if (mouseX >= plusX && mouseX <= plusX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            value = (value + step).coerceIn(min, max)
            onValueChanged?.invoke(value)
            return true
        }

        return false
    }
}
