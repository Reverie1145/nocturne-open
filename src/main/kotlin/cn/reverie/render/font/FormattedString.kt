package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGColor

data class TextSpan(
    val text: String,
    val color: NanoVGColor = NanoVGColor.WHITE,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val clickAction: (() -> Unit)? = null,
    val tooltip: String? = null
)

class FormattedString(val spans: List<TextSpan>) {

    constructor(text: String, defaultColor: NanoVGColor = NanoVGColor.WHITE) : this(
        ColorCodeParser.parse(text, defaultColor)
    )

    val rawText: String get() = spans.joinToString("") { it.text }
    val length: Int get() = rawText.length

    companion object {
        val EMPTY = FormattedString(emptyList())

        fun of(text: String, color: NanoVGColor = NanoVGColor.WHITE): FormattedString {
            return FormattedString(text, color)
        }
    }
}
