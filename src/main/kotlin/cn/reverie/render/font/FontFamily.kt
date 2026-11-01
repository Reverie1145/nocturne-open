package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGFont

data class FontFamily(
    val name: String,
    val regular: NanoVGFont,
    val bold: NanoVGFont? = null,
    val italic: NanoVGFont? = null,
    val boldItalic: NanoVGFont? = null,
    val medium: NanoVGFont? = null,
    val light: NanoVGFont? = null
) {
    fun select(bold: Boolean = false, italic: Boolean = false): NanoVGFont {
        return when {
            bold && italic -> boldItalic ?: this.bold ?: this.italic ?: regular
            bold -> this.bold ?: regular
            italic -> this.italic ?: regular
            else -> regular
        }
    }
}
