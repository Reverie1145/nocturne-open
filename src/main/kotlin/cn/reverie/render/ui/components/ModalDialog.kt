package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.UIContainer
import cn.reverie.render.ui.UITheme

class ModalDialog(
    var title: String,
    var message: String,
    var theme: UITheme = UITheme.DARK
) : UIContainer() {

    init {
        width = 280f
        height = 160f
    }

    var isOpen = false
    val openAnim = FloatAnimation(0f, 0f, 200L)

    val confirmBtn = ButtonWidget("Confirm", theme)
    val cancelBtn = ButtonWidget("Cancel", theme)

    init {
        confirmBtn.width = 100f
        confirmBtn.height = 28f
        cancelBtn.width = 80f
        cancelBtn.height = 28f

        add(confirmBtn)
        add(cancelBtn)
    }

    fun open() {
        isOpen = true
        isVisible = true
    }

    fun close() {
        isOpen = false
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        openAnim.animateTo(if (isOpen) 1f else 0f)
        if (openAnim.value <= 0.01f) {
            isVisible = false
            return
        }

        RenderEngine.blurEngine.backdropRenderer.renderBackdrop(
            absoluteBounds,
            radius = 12f,
            tintColor = theme.surface.withAlpha(0.9f * openAnim.value),
            blurRadius = 24f
        )

        Draw2D.shadow(absoluteBounds, 12f, 24f, NanoVGColor(0f, 0f, 0f, 0.5f * openAnim.value), 8f)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 12f, theme.border)

        val font = RenderEngine.fontRenderer
        font.drawString(title, absoluteX + 16f, absoluteY + 16f, theme.textPrimary, 16f)
        font.drawWrappedString(message, absoluteX + 16f, absoluteY + 44f, width - 32f, theme.textSecondary, 13f)

        confirmBtn.x = width - 116f
        confirmBtn.y = height - 44f
        cancelBtn.x = width - 204f
        cancelBtn.y = height - 44f

        super.render(mouseX, mouseY, partialTicks)
    }
}
