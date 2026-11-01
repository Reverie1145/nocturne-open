package cn.reverie.render.font

import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGFont
import org.lwjgl.nanovg.NanoVG.*

class FontRenderer(val defaultFont: NanoVGFont) {

    var ambientBlur: Float = 0f

    var activeFont: NanoVGFont = defaultFont
        private set

    var sizeScale: Float = 1f
        private set

    var verticalBias: Float = 0f
        private set

    inline fun <T> withAmbientBlur(blur: Float, block: () -> T): T {
        val prev = ambientBlur
        ambientBlur = blur
        return try {
            block()
        } finally {
            ambientBlur = prev
            if (prev <= 0.005f) {
                activeFont.setBlur(0f)
            } else {
                activeFont.setBlur(prev)
            }
        }
    }

    fun <T> withFont(
        font: NanoVGFont,
        sizeScale: Float = 1f,
        verticalBias: Float = 0f,
        block: () -> T
    ): T {
        val prevFont = activeFont
        val prevScale = this.sizeScale
        val prevBias = this.verticalBias
        activeFont = font
        this.sizeScale = sizeScale
        this.verticalBias = verticalBias
        return try {
            block()
        } finally {
            activeFont = prevFont
            this.sizeScale = prevScale
            this.verticalBias = prevBias
        }
    }

    fun resolveSize(fontSize: Float): Float = fontSize * sizeScale

    private fun biasFor(vAlign: Int, size: Float): Float {
        if (verticalBias == 0f) return 0f
        return when (vAlign) {
            NVG_ALIGN_MIDDLE, NVG_ALIGN_TOP -> verticalBias * size
            else -> 0f
        }
    }

    fun drawString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 14f,
        alignment: TextAlignment = TextAlignment.TOP_LEFT,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        val spans = ColorCodeParser.parse(text, color)
        return drawSpans(spans, x, y, fontSize, alignment, shadow, blur)
    }

    fun drawCenteredString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 14f,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        return drawString(text, x, y, color, fontSize, TextAlignment.MIDDLE_CENTER, shadow, blur)
    }

    fun drawBlurTransitionString(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 14f,
        progress: Float,
        maxBlur: Float = 5.5f,
        alignment: TextAlignment = TextAlignment.TOP_LEFT,
        shadow: Boolean = false
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val blur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(p, maxBlur)
        val finalColor = color.multiplyAlpha(p)
        return drawString(text, x, y, finalColor, fontSize, alignment, shadow, blur)
    }

    fun drawBlurTransitionCentered(
        text: String,
        x: Float,
        y: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 14f,
        progress: Float,
        maxBlur: Float = 5.5f,
        shadow: Boolean = false
    ): Float {
        return drawBlurTransitionString(text, x, y, color, fontSize, progress, maxBlur, TextAlignment.MIDDLE_CENTER, shadow)
    }

    fun drawSpans(
        spans: List<TextSpan>,
        startX: Float,
        startY: Float,
        fontSize: Float = 14f,
        alignment: TextAlignment = TextAlignment.TOP_LEFT,
        shadow: Boolean = false,
        blur: Float = 0f
    ): Float {
        if (spans.isEmpty()) return 0f
        val drawSize = resolveSize(fontSize)
        val totalWidth = spans.sumOf { activeFont.measureWidth(it.text, drawSize).toDouble() }.toFloat()

        var currentX = when (alignment) {
            TextAlignment.TOP_CENTER, TextAlignment.MIDDLE_CENTER, TextAlignment.BOTTOM_CENTER, TextAlignment.BASELINE_CENTER -> startX - totalWidth * 0.5f
            TextAlignment.TOP_RIGHT, TextAlignment.MIDDLE_RIGHT, TextAlignment.BOTTOM_RIGHT, TextAlignment.BASELINE_RIGHT -> startX - totalWidth
            else -> startX
        }

        val vAlign = when (alignment) {
            TextAlignment.MIDDLE_LEFT, TextAlignment.MIDDLE_CENTER, TextAlignment.MIDDLE_RIGHT -> NVG_ALIGN_MIDDLE
            TextAlignment.BOTTOM_LEFT, TextAlignment.BOTTOM_CENTER, TextAlignment.BOTTOM_RIGHT -> NVG_ALIGN_BOTTOM
            TextAlignment.BASELINE_LEFT, TextAlignment.BASELINE_CENTER, TextAlignment.BASELINE_RIGHT -> NVG_ALIGN_BASELINE
            else -> NVG_ALIGN_TOP
        }

        val drawY = startY + biasFor(vAlign, drawSize)
        activeFont.prepare(drawSize, NVG_ALIGN_LEFT or vAlign)

        val effBlur = if (blur > 0.005f) blur else ambientBlur
        val needBlur = effBlur > 0.005f
        if (needBlur) {
            activeFont.setBlur(effBlur)
        }

        try {
            for (span in spans) {
                val spanWidth = activeFont.measureWidth(span.text, drawSize)

                if (shadow) {
                    val shadowColor = NanoVGColor(0f, 0f, 0f, span.color.a * 0.6f)
                    activeFont.drawText(span.text, currentX + 1.0f, drawY + 1.0f, shadowColor, drawSize)
                }

                activeFont.drawText(span.text, currentX, drawY, span.color, drawSize)
                currentX += spanWidth
            }
        } finally {
            if (needBlur) {
                activeFont.setBlur(if (ambientBlur > 0.005f) ambientBlur else 0f)
            }
        }

        return totalWidth
    }

    fun drawWrappedString(
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: NanoVGColor = NanoVGColor.WHITE,
        fontSize: Float = 14f,
        lineSpacing: Float = 4.0f
    ): Float {
        val drawSize = resolveSize(fontSize)
        val layout = TextLayout(activeFont, text, drawSize, maxWidth, lineSpacing)
        for (line in layout.lines) {
            drawString(line.text, x, y + line.yOffset, color, fontSize)
        }
        return layout.totalHeight
    }

    fun getStringWidth(text: String, fontSize: Float = 14f): Float {
        val drawSize = resolveSize(fontSize)
        if (!text.contains('§') && !text.contains('&')) {
            return activeFont.measureWidth(text, drawSize)
        }
        val spans = ColorCodeParser.parse(text)
        if (spans.isEmpty()) return 0f
        return spans.sumOf { activeFont.measureWidth(it.text, drawSize).toDouble() }.toFloat()
    }

    fun getStringHeight(fontSize: Float = 14f): Float {
        return activeFont.measureHeight(resolveSize(fontSize))
    }

    fun trimWithEllipsis(text: String, maxWidth: Float, fontSize: Float = 14f): String {
        val drawSize = resolveSize(fontSize)
        val fullWidth = activeFont.measureWidth(text, drawSize)
        if (fullWidth <= maxWidth) return text

        val ellipsis = "..."
        val ellipsisWidth = activeFont.measureWidth(ellipsis, drawSize)
        if (ellipsisWidth >= maxWidth) return ""

        val availableWidth = maxWidth - ellipsisWidth
        var low = 0
        var high = text.length
        var best = 0

        while (low <= high) {
            val mid = (low + high) / 2
            val sub = text.substring(0, mid)
            if (activeFont.measureWidth(sub, drawSize) <= availableWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return text.substring(0, best) + ellipsis
    }
}
