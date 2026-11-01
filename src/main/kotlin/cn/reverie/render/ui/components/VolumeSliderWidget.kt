package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

class VolumeSliderWidget(
    var volume: Float = 0.8f,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 140f
        height = 24f
    }

    var onVolumeChanged: ((Float) -> Unit)? = null
    private var isDragging = false

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        val speakerIcon = when {
            volume <= 0.01f -> "🔇"
            volume < 0.5f -> "🔉"
            else -> "🔊"
        }
        font.drawString(speakerIcon, absoluteX, absoluteY + 5f, theme.textPrimary, 12f)

        val trackX = absoluteX + 24f
        val trackW = width - 24f
        val trackY = absoluteY + (height - 4f) * 0.5f
        val trackH = 4f

        Draw2D.roundedRect(trackX, trackY, trackW, trackH, 2f, theme.surfaceActive)

        val fillW = trackW * volume.coerceIn(0f, 1f)
        if (fillW > 0.5f) {
            Draw2D.roundedRect(trackX, trackY, fillW, trackH, 2f, theme.primary)
        }

        val thumbX = trackX + fillW
        val thumbY = trackY + trackH * 0.5f
        Draw2D.circle(thumbX, thumbY, 5f, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        val trackX = absoluteX + 24f
        val trackW = width - 24f
        if (mouseX >= trackX && mouseX <= trackX + trackW && isMouseOver(mouseX, mouseY)) {
            isDragging = true
            updateVolume(mouseX)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = isDragging
        isDragging = false
        return was
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDragging) {
            updateVolume(mouseX)
            return true
        }
        return false
    }

    private fun updateVolume(mouseX: Float) {
        val trackX = absoluteX + 24f
        val trackW = width - 24f
        val v = ((mouseX - trackX) / trackW).coerceIn(0f, 1f)
        if (volume != v) {
            volume = v
            onVolumeChanged?.invoke(volume)
        }
    }
}
