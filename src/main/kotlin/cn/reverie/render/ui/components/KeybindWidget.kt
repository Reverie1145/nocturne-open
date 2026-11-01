package cn.reverie.render.ui.components

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme
import org.lwjgl.glfw.GLFW.*

class KeybindWidget(
    var label: String,
    var keyCode: Int = GLFW_KEY_UNKNOWN,
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 140f
        height = 24f
    }

    var isListening = false
    var onKeybindChanged: ((Int) -> Unit)? = null

    private fun getKeyName(): String {
        if (isListening) return "..."
        if (keyCode == GLFW_KEY_UNKNOWN) return "None"
        val name = glfwGetKeyName(keyCode, 0)
        return name?.uppercase() ?: "Key $keyCode"
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        val font = RenderEngine.fontRenderer
        font.drawString(label, absoluteX, absoluteY + 5f, theme.textSecondary, 12f)

        val keyName = getKeyName()
        val keyW = maxOf(40f, font.getStringWidth(keyName, 11f) + 16f)
        val keyX = absoluteX + width - keyW
        val keyH = 20f
        val keyY = absoluteY + (height - keyH) * 0.5f

        val bg = if (isListening) theme.primary else theme.surface
        Draw2D.roundedRect(keyX, keyY, keyW, keyH, 4f, bg)
        Draw2D.roundedOutline(keyX, keyY, keyW, keyH, 4f, if (isListening) theme.borderFocus else theme.border)

        font.drawCenteredString(
            keyName,
            keyX + keyW * 0.5f,
            keyY + 4f,
            if (isListening) cn.reverie.render.nanovg.NanoVGColor.WHITE else theme.textPrimary,
            11f
        )
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isVisible || !isEnabled) return false
        if (isMouseOver(mouseX, mouseY)) {
            isListening = !isListening
            return true
        } else {
            isListening = false
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isListening) {
            if (keyCode == GLFW_KEY_ESCAPE) {
                this.keyCode = GLFW_KEY_UNKNOWN
            } else {
                this.keyCode = keyCode
            }
            isListening = false
            onKeybindChanged?.invoke(this.keyCode)
            return true
        }
        return false
    }
}
