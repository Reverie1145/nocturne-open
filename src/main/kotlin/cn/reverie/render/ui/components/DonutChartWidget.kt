package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.ArcRenderer
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class DonutChartWidget(
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    data class Segment(val label: String, val value: Float, val color: NanoVGColor)

    val segments = ArrayList<Segment>()

    init {
        width = 120f
        height = 120f
    }

    fun addSegment(label: String, value: Float, color: NanoVGColor) {
        segments.add(Segment(label, value, color))
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible || segments.isEmpty()) return

        val cx = absoluteX + width * 0.5f
        val cy = absoluteY + height * 0.5f
        val radius = width * 0.35f
        val strokeW = 12f

        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0.001f) return

        var startAngle = -Math.PI.toFloat() * 0.5f

        for (seg in segments) {
            val sweep = (seg.value / total) * Math.PI.toFloat() * 2f
            val endAngle = startAngle + sweep
            ArcRenderer.drawArc(RenderEngine.nanoVG.handle, cx, cy, radius, startAngle, endAngle, seg.color, strokeW, false)
            startAngle = endAngle
        }
    }
}
