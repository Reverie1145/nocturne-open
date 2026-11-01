package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.draw.LineRenderer
import cn.reverie.render.math.Vec2f
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class GraphWidget(
    val title: String,
    val maxDataPoints: Int = 50,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 180f
        height = 80f
    }

    private val dataPoints = ArrayList<Float>()

    fun addPoint(value: Float) {
        if (dataPoints.size >= maxDataPoints) {
            dataPoints.removeAt(0)
        }
        dataPoints.add(value)
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        Draw2D.roundedRect(absoluteBounds, 6f, theme.surface)
        Draw2D.roundedOutline(absoluteBounds, 6f, theme.border)

        val font = RenderEngine.fontRenderer
        font.drawString(title, absoluteX + 8f, absoluteY + 6f, theme.textSecondary, 11f)

        if (dataPoints.size < 2) return

        val graphX = absoluteX + 8f
        val graphY = absoluteY + 24f
        val graphW = width - 16f
        val graphH = height - 32f

        val maxVal = maxOf(1f, dataPoints.maxOrNull() ?: 1f)
        val minVal = dataPoints.minOrNull() ?: 0f

        val points = ArrayList<Vec2f>()
        val stepX = graphW / (maxDataPoints - 1)

        for (i in dataPoints.indices) {
            val px = graphX + i * stepX
            val py = graphY + graphH - ((dataPoints[i] - minVal) / maxOf(0.001f, maxVal - minVal)) * graphH
            points.add(Vec2f(px, py))
        }

        LineRenderer.drawPolyline(RenderEngine.nanoVG.handle, points, theme.primary, 1.5f)
    }
}
