package cn.reverie.render.ui.components
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import org.lwjgl.glfw.GLFW.*
import cn.reverie.render.ui.UIElement
import cn.reverie.render.ui.UITheme

open class TextInputWidget(
    var text: String = "",
    var placeholder: String = "Type here...",
    var theme: UITheme = UITheme.DARK
) : UIElement() {

    init {
        width = 180f
        height = 32f
    }

    var cursorPosition: Int = text.length
    var isPassword: Boolean = false
    var onTextChanged: ((String) -> Unit)? = null

    private val focusAnim = FloatAnimation(0f, 0f, 150L)
    private var blinkTimer = 0L

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (!isVisible) return

        focusAnim.animateTo(if (isFocused) 1f else 0f)

        Draw2D.roundedRect(absoluteX, absoluteY, width, height, 6f, theme.surface)
        val borderColor = theme.border.lerp(theme.borderFocus, focusAnim.value)
        Draw2D.roundedOutline(absoluteX, absoluteY, width, height, 6f, borderColor, if (isFocused) 1.5f else 1.0f)

        val font = RenderEngine.fontRenderer
        val displayStr = if (isPassword) "•".repeat(text.length) else text

        if (displayStr.isEmpty() && !isFocused) {
            font.drawString(placeholder, absoluteX + 10f, absoluteY + 9f, theme.textDisabled, 13f)
        } else {
            font.drawString(displayStr, absoluteX + 10f, absoluteY + 9f, theme.textPrimary, 13f)
        }

        if (isFocused) {
            val cursorSub = if (isPassword) "•".repeat(cursorPosition) else text.substring(0, cursorPosition.coerceIn(0, text.length))
            val cursorX = absoluteX + 10f + font.getStringWidth(cursorSub, 13f)
            val blink = (System.currentTimeMillis() / 500) % 2 == 0L

            if (blink) {
                Draw2D.line(cursorX, absoluteY + 7f, cursorX, absoluteY + height - 7f, theme.primary, 1.5f)
            }
        }
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isFocused) return false

        when (keyCode) {
            GLFW_KEY_BACKSPACE -> {
                if (cursorPosition > 0 && text.isNotEmpty()) {
                    text = text.substring(0, cursorPosition - 1) + text.substring(cursorPosition)
                    cursorPosition--
                    onTextChanged?.invoke(text)
                    return true
                }
            }
            GLFW_KEY_DELETE -> {
                if (cursorPosition < text.length) {
                    text = text.substring(0, cursorPosition) + text.substring(cursorPosition + 1)
                    onTextChanged?.invoke(text)
                    return true
                }
            }
            GLFW_KEY_LEFT -> {
                if (cursorPosition > 0) cursorPosition--
                return true
            }
            GLFW_KEY_RIGHT -> {
                if (cursorPosition < text.length) cursorPosition++
                return true
            }
            GLFW_KEY_HOME -> {
                cursorPosition = 0
                return true
            }
            GLFW_KEY_END -> {
                cursorPosition = text.length
                return true
            }
        }
        return false
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!isFocused) return false
        if (codePoint.code >= 32 && codePoint.code != 127) {
            text = text.substring(0, cursorPosition) + codePoint + text.substring(cursorPosition)
            cursorPosition++
            onTextChanged?.invoke(text)
            return true
        }
        return false
    }
}
