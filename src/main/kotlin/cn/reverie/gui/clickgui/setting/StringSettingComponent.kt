package cn.reverie.gui.clickgui.setting
import cn.reverie.module.StringSetting
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW

class StringSettingComponent(
    val stringSetting: StringSetting,
    private val maxLength: Int = MAX_LENGTH,
    private val placeholder: String = "Click to enter a name"
) : SettingComponent(stringSetting) {

    private var focused = false
    private var cursor = stringSetting.value.length

    init {
        height = 37f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val labelY = y + 1f
        val fieldX = x + 5f
        val fieldY = y + 16f
        val fieldW = width - 10f
        val fieldH = 18f
        val hovered = mouseX in fieldX..(fieldX + fieldW) && mouseY in fieldY..(fieldY + fieldH)

        NanoVGRenderer.drawString(
            stringSetting.name,
            x + 7f,
            labelY,
            NanoVGColor(0.72f, 0.74f, 0.80f, 0.92f),
            8.5f
        )
        NanoVGRenderer.drawRoundedRect(
            fieldX,
            fieldY,
            fieldW,
            fieldH,
            4f,
            NanoVGColor(0f, 0f, 0f, if (focused) 0.35f else 0.22f)
        )
        NanoVGRenderer.drawRoundedOutline(
            fieldX,
            fieldY,
            fieldW,
            fieldH,
            4f,
            when {
                focused -> NanoVGColor(0.91f, 0.92f, 0.94f, 0.42f)
                hovered -> NanoVGColor(1f, 1f, 1f, 0.18f)
                else -> NanoVGColor(1f, 1f, 1f, 0.08f)
            },
            0.8f
        )

        val text = stringSetting.value
        val textX = fieldX + 7f
        val centerY = fieldY + fieldH * 0.5f
        val availableW = fieldW - 14f
        if (text.isEmpty()) {
            NanoVGRenderer.drawVerticallyCenteredString(
                placeholder,
                textX,
                centerY,
                NanoVGColor(0.43f, 0.45f, 0.51f, 0.88f),
                8.5f
            )
            if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                NanoVGRenderer.drawRoundedRect(
                    textX,
                    fieldY + 4f,
                    1f,
                    fieldH - 8f,
                    0.5f,
                    NanoVGColor(0.91f, 0.93f, 0.98f, 0.95f)
                )
            }
        } else {
            cursor = cursor.coerceIn(0, text.length)
            var start = cursor
            while (start > 0 && NanoVGRenderer.getTextWidth(text.substring(start, cursor), 8.5f) < availableW * 0.45f) {
                start--
            }
            var end = cursor
            while (end < text.length && NanoVGRenderer.getTextWidth(text.substring(start, end + 1), 8.5f) <= availableW) {
                end++
            }
            val visibleText = text.substring(start, end)
            NanoVGRenderer.withScissor(textX, fieldY + 1f, availableW, fieldH - 2f) {
                NanoVGRenderer.drawVerticallyCenteredString(
                    visibleText,
                    textX,
                    centerY,
                    NanoVGColor(0.91f, 0.92f, 0.95f, 0.96f),
                    8.5f
                )
                if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                    val caretX = textX + NanoVGRenderer.getTextWidth(text.substring(start, cursor), 8.5f)
                    NanoVGRenderer.drawRoundedRect(
                        caretX,
                        fieldY + 4f,
                        1f,
                        fieldH - 8f,
                        0.5f,
                        NanoVGColor(0.91f, 0.93f, 0.98f, 0.95f)
                    )
                }
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val fieldY = y + 16f
        if (button != 0 || mouseX !in (x + 5f)..(x + width - 5f) || mouseY !in fieldY..(fieldY + 18f)) return false
        clearFocus()
        focused = true
        activeInput = this
        cursor = stringSetting.value.length
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!focused) return false
        val text = stringSetting.value
        cursor = cursor.coerceIn(0, text.length)
        val ctrl = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> focused = false
            GLFW.GLFW_KEY_BACKSPACE -> if (cursor > 0) {
                stringSetting.value = text.removeRange(cursor - 1, cursor)
                cursor--
            }
            GLFW.GLFW_KEY_DELETE -> if (cursor < text.length) {
                stringSetting.value = text.removeRange(cursor, cursor + 1)
            }
            GLFW.GLFW_KEY_LEFT -> cursor = (cursor - 1).coerceAtLeast(0)
            GLFW.GLFW_KEY_RIGHT -> cursor = (cursor + 1).coerceAtMost(text.length)
            GLFW.GLFW_KEY_HOME -> cursor = 0
            GLFW.GLFW_KEY_END -> cursor = text.length
            GLFW.GLFW_KEY_A -> if (ctrl) {
                stringSetting.value = ""
                cursor = 0
            } else return false
            GLFW.GLFW_KEY_V -> if (ctrl) pasteFromClipboard() else return false
            else -> return false
        }
        return true
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!focused || codePoint.isISOControl() || stringSetting.value.length >= maxLength) return false
        val text = stringSetting.value
        cursor = cursor.coerceIn(0, text.length)
        stringSetting.value = text.substring(0, cursor) + codePoint + text.substring(cursor)
        cursor++
        return true
    }

    private fun pasteFromClipboard() {
        val window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext()
        val pasted = runCatching { GLFW.glfwGetClipboardString(window) }.getOrNull() ?: return
        val clean = pasted.filterNot { it.isISOControl() }
        val available = (maxLength - stringSetting.value.length).coerceAtLeast(0)
        if (available == 0) return
        val insertion = clean.take(available)
        val text = stringSetting.value
        cursor = cursor.coerceIn(0, text.length)
        stringSetting.value = text.substring(0, cursor) + insertion + text.substring(cursor)
        cursor += insertion.length
    }

    companion object {
        private var activeInput: StringSettingComponent? = null
        const val MAX_LENGTH = 32

        fun hasFocusedInput(): Boolean = activeInput?.focused == true

        fun clearFocus() {
            activeInput?.focused = false
            activeInput = null
        }
    }
}
