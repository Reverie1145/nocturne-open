package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class ProgressBarWidget(
    var progress: Float = 0f,
    var isIndeterminate: Boolean = false,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 160f
        height = 6f
    }

    private val progressAnim = FloatAnimation(progress, progress, 150L)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        progressAnim.animateTo(progress.coerceIn(0f, 1f))
        val radius = height * 0.5f

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, radius, theme.surfaceActive)

        if (isIndeterminate) {
            val t = (System.currentTimeMillis() % 1500) / 1500f
            val barW = width * 0.3f
            val barX = absoluteX + (width + barW) * t - barW
            Draw2D.roundedRect(barX.coerceIn(absoluteX, absoluteX + width - barW), absoluteY, barW, height, radius, theme.primary)
        } else {
            val fillW = width * progressAnim.value
            if (fillW > 0.5f) {
                Draw2D.roundedRect(absoluteX, absoluteY, fillW, height, radius, theme.primary)
            }
        }
    }
}
