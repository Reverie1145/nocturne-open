package cn.reverie.render.font

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGFont
import cn.reverie.render.theme.Theme
import kotlin.math.ceil

class SkiaFontRenderer internal constructor(
    private val renderer: FontRenderer,
    private val nvg: NanoVGFont,
    val size: Int
) {
    private val guiSize = size / 2f

    val height: Int get() = ceil(guiSize).toInt().coerceAtLeast(1)
    fun getHalfHeight(): Float = height / 2f
    val halfHeight: Float @JvmName("halfHeightVal") get() = getHalfHeight()

    fun getStringWidth(text: String): Float = renderer.getStringWidth(text, guiSize)

    fun drawString(text: String, x: Float, y: Float, color: NanoVGColor): Float {
        return renderer.drawString(text, x, y, color, guiSize, TextAlignment.TOP_LEFT, shadow = false)
    }

    fun drawStringWithShadow(text: String, x: Float, y: Float, color: NanoVGColor): Float {
        renderer.drawString(text, x + 0.5f, y + 0.5f, NanoVGColor(0f, 0f, 0f, color.a * 0.65f), guiSize, TextAlignment.TOP_LEFT, shadow = false)
        return renderer.drawString(text, x, y, color, guiSize, TextAlignment.TOP_LEFT, shadow = false)
    }

    fun drawStringWithFade(text: String, x: Float, y: Float, alpha: Float = 1.0f, shadow: Boolean = false): Float {
        var currentX = x
        var colorIndex = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                i += 2
                continue
            }
            val charW = renderer.getStringWidth(text[i].toString(), guiSize)
            val charColor = Theme.getClientColor(colorIndex, alpha)
            if (shadow) {
                renderer.drawString(text[i].toString(), currentX + 0.5f, y + 0.5f, NanoVGColor(0f, 0f, 0f, alpha * 0.65f), guiSize, TextAlignment.TOP_LEFT, shadow = false)
            }
            renderer.drawString(text[i].toString(), currentX, y, charColor, guiSize, TextAlignment.TOP_LEFT, shadow = false)
            currentX += charW
            colorIndex++
            i++
        }
        return currentX - x
    }
}
