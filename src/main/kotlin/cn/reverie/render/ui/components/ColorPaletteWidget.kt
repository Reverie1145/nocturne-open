package cn.reverie.render.ui.components

import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ColorPaletteWidget(
    val colors: List<NanoVGColor> = DEFAULT_PALETTE,
    var selectedIndex: Int = 0,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 36f
    }

    var onColorSelected: ((NanoVGColor) -> Unit)? = null

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val swatchSize = 18f
        val gap = 6f

        for (i in colors.indices) {
            val sx = absoluteX + i * (swatchSize + gap)
            val sy = absoluteY + (height - swatchSize) * 0.5f

            Draw2D.circle(sx + swatchSize * 0.5f, sy + swatchSize * 0.5f, swatchSize * 0.5f, colors[i])
            if (i == selectedIndex) {
                Draw2D.circleOutline(sx + swatchSize * 0.5f, sy + swatchSize * 0.5f, swatchSize * 0.5f + 2f, theme.primary, 1.5f)
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        val swatchSize = 18f
        val gap = 6f
        for (i in colors.indices) {
            val sx = absoluteX + i * (swatchSize + gap)
            val sy = absoluteY + (height - swatchSize) * 0.5f
            if (mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= sy && mouseY <= sy + swatchSize) {
                selectedIndex = i
                onColorSelected?.invoke(colors[i])
                return true
            }
        }
        return false
    }

    companion object {
        val DEFAULT_PALETTE = listOf(
            NanoVGColor.fromHex("#FF5252"),
            NanoVGColor.fromHex("#FF4081"),
            NanoVGColor.fromHex("#E040FB"),
            NanoVGColor.fromHex("#7C4DFF"),
            NanoVGColor.fromHex("#536DFE"),
            NanoVGColor.fromHex("#448AFF")
        )
    }
}
