package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class CodeEditorWidget(
    var codeLines: MutableList<String> = mutableListOf("fun main() {", "    println(\"Hello, Nocturne!\")", "}"),
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 240f
        height = 140f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteBounds, 8f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 8f, theme.border)

        val gutterW = 28f
        Draw2D.roundedRect(absoluteX, absoluteY, gutterW, height, 8f, theme.background)
        Draw2D.line(absoluteX + gutterW, absoluteY, absoluteX + gutterW, absoluteY + height, theme.border, 1.0f)

        val font = RenderEngine.fontRenderer
        val lineH = 16f

        for (i in codeLines.indices) {
            val lineY = absoluteY + 8f + i * lineH
            val lineNum = (i + 1).toString()

            font.drawString(lineNum, absoluteX + 8f, lineY, theme.textDisabled, 11f)

            font.drawString(codeLines[i], absoluteX + gutterW + 8f, lineY, theme.textPrimary, 11f)
        }
    }
}
