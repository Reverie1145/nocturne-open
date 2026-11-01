package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGColor

object ColorCodeParser {

    private val MINECRAFT_COLORS = mapOf(
        '0' to NanoVGColor.fromArgb(0xFF000000.toInt()),
        '1' to NanoVGColor.fromArgb(0xFF0000AA.toInt()),
        '2' to NanoVGColor.fromArgb(0xFF00AA00.toInt()),
        '3' to NanoVGColor.fromArgb(0xFF00AAAA.toInt()),
        '4' to NanoVGColor.fromArgb(0xFFAA0000.toInt()),
        '5' to NanoVGColor.fromArgb(0xFFAA00AA.toInt()),
        '6' to NanoVGColor.fromArgb(0xFFFFAA00.toInt()),
        '7' to NanoVGColor.fromArgb(0xFFAAAAAA.toInt()),
        '8' to NanoVGColor.fromArgb(0xFF555555.toInt()),
        '9' to NanoVGColor.fromArgb(0xFF5555FF.toInt()),
        'a' to NanoVGColor.fromArgb(0xFF55FF55.toInt()),
        'b' to NanoVGColor.fromArgb(0xFF55FFFF.toInt()),
        'c' to NanoVGColor.fromArgb(0xFFFF5555.toInt()),
        'd' to NanoVGColor.fromArgb(0xFFFF55FF.toInt()),
        'e' to NanoVGColor.fromArgb(0xFFFFFF55.toInt()),
        'f' to NanoVGColor.fromArgb(0xFFFFFFFF.toInt())
    )

    fun stripColorCodes(input: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '§' && i + 1 < input.length) {
                if (input[i + 1] == '#' && i + 7 < input.length && input.substring(i + 2, i + 8).all { it.isHexDigit() }) {
                    i += 8
                    continue
                }
                i += 2
                continue
            }
            if (c == '&' && i + 7 < input.length && input[i + 1] == '#' && input.substring(i + 2, i + 8).all { it.isHexDigit() }) {
                i += 8
                continue
            }
            result.append(c)
            i++
        }
        return result.toString()
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun parse(input: String, defaultColor: NanoVGColor = NanoVGColor.WHITE): List<TextSpan> {
        val spans = ArrayList<TextSpan>()
        val currentText = StringBuilder()
        var currentColor = defaultColor
        var bold = false
        var italic = false
        var underline = false
        var strikethrough = false

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '§' && i + 1 < input.length) {
                if (input[i + 1] == '#' && i + 7 < input.length) {
                    val hex = input.substring(i + 2, i + 8)
                    if (hex.all { it.isHexDigit() }) {
                        if (currentText.isNotEmpty()) {
                            spans.add(TextSpan(currentText.toString(), currentColor, bold, italic, underline, strikethrough))
                            currentText.clear()
                        }
                        currentColor = NanoVGColor.fromHex(hex)
                        i += 8
                        continue
                    }
                }

                val code = input[i + 1].lowercaseChar()
                if (currentText.isNotEmpty()) {
                    spans.add(TextSpan(currentText.toString(), currentColor, bold, italic, underline, strikethrough))
                    currentText.clear()
                }

                when (code) {
                    in '0'..'9', in 'a'..'f' -> {
                        val baseColor = MINECRAFT_COLORS[code] ?: defaultColor
                        currentColor = baseColor.multiplyAlpha(defaultColor.a)
                        bold = false; italic = false; underline = false; strikethrough = false
                    }
                    'l' -> bold = true
                    'o' -> italic = true
                    'n' -> underline = true
                    'm' -> strikethrough = true
                    'r' -> {
                        currentColor = defaultColor
                        bold = false; italic = false; underline = false; strikethrough = false
                    }
                }
                i += 2
                continue
            }

            if (c == '&' && i + 7 < input.length && input[i + 1] == '#') {
                val hex = input.substring(i + 2, i + 8)
                if (hex.all { it.isHexDigit() }) {
                    if (currentText.isNotEmpty()) {
                        spans.add(TextSpan(currentText.toString(), currentColor, bold, italic, underline, strikethrough))
                        currentText.clear()
                    }
                    currentColor = NanoVGColor.fromHex(hex)
                    i += 8
                    continue
                }
            }

            currentText.append(c)
            i++
        }

        if (currentText.isNotEmpty()) {
            spans.add(TextSpan(currentText.toString(), currentColor, bold, italic, underline, strikethrough))
        }

        return spans
    }
}
