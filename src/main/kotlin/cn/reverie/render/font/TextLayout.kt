package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGFont

class TextLayout(
    val font: NanoVGFont,
    val text: String,
    val fontSize: Float,
    val maxWidth: Float = Float.MAX_VALUE,
    val lineSpacing: Float = 4.0f
) {
    data class Line(val text: String, val width: Float, val yOffset: Float)

    val lines = ArrayList<Line>()
    var totalWidth: Float = 0f
        private set
    var totalHeight: Float = 0f
        private set

    init {
        layout()
    }

    private fun layout() {
        lines.clear()
        val lineHeight = font.measureHeight(fontSize) + lineSpacing
        val rawLines = text.split("\n")

        var currentY = 0f

        for (rawLine in rawLines) {
            val words = rawLine.split(" ")
            val currentLineWords = StringBuilder()

            for (word in words) {
                val candidate = if (currentLineWords.isEmpty()) word else "$currentLineWords $word"
                val candidateWidth = font.measureWidth(candidate, fontSize)

                if (candidateWidth <= maxWidth || currentLineWords.isEmpty()) {
                    currentLineWords.clear()
                    currentLineWords.append(candidate)
                } else {
                    val lineWidth = font.measureWidth(currentLineWords.toString(), fontSize)
                    lines.add(Line(currentLineWords.toString(), lineWidth, currentY))
                    totalWidth = maxOf(totalWidth, lineWidth)
                    currentY += lineHeight

                    currentLineWords.clear()
                    currentLineWords.append(word)
                }
            }

            if (currentLineWords.isNotEmpty()) {
                val lineWidth = font.measureWidth(currentLineWords.toString(), fontSize)
                lines.add(Line(currentLineWords.toString(), lineWidth, currentY))
                totalWidth = maxOf(totalWidth, lineWidth)
                currentY += lineHeight
            }
        }

        totalHeight = if (lines.isEmpty()) 0f else currentY - lineSpacing
    }
}
