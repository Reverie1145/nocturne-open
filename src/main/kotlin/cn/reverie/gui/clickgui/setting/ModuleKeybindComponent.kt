package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.ClickGUIModule
class ModuleKeybindComponent(val module: Module) : SettingComponent(
    BooleanSetting("Keybind", "Keybind for ${module.name}", false)
) {
    init {
        height = 19f
    }

    var isListening = false
    private val hoverAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        hoverAnim.durationMs = (180L / animMultiplier).toLong().coerceIn(40L, 400L)
        hoverAnim.animateTo(if (isMouseOver(mouseX, mouseY)) 1f else 0f)

        val centerY = y + height * 0.5f
        val bindText = if (isListening) "..." else if (module.keyCode != GLFW.GLFW_KEY_UNKNOWN) getKeyName(module.keyCode) else "NONE"
        val textW = NanoVGRenderer.getTextWidth(bindText, 9f)
        val pillW = (textW + 10f).coerceAtLeast(26f)
        val pillH = 12f
        val pillX = x + width - pillW - 6f
        val pillY = centerY - pillH * 0.5f

        val maxTextBindW = pillX - (x + 8f) - 4f
        val titleColor = NanoVGColor(0.70f, 0.73f, 0.80f, 0.85f).lerp(NanoVGColor.WHITE, hoverAnim.value * 0.4f)
        NanoVGRenderer.drawMarqueeString(
            "Keybind",
            x + 8f,
            centerY,
            maxTextBindW,
            isMouseOver(mouseX, mouseY),
            titleColor,
            10.0f
        )

        val idleBg = NanoVGColor(0f, 0f, 0f, 0.18f)
        val hoverBg = NanoVGColor(1f, 1f, 1f, 0.06f)
        val listenBg = NanoVGColor(0.25f, 0.26f, 0.32f, 0.95f)

        val bg = if (isListening) listenBg else idleBg.lerp(hoverBg, hoverAnim.value)
        NanoVGRenderer.drawRoundedRect(pillX, pillY, pillW, pillH, 2.5f, bg)

        if (isListening) {
            val pulse = (kotlin.math.sin(System.currentTimeMillis() * 0.008).toFloat() * 0.5f + 0.5f)
            val outlineColor = NanoVGColor(1f, 1f, 1f, 0.35f + 0.35f * pulse)
            NanoVGRenderer.drawRoundedOutline(pillX, pillY, pillW, pillH, 2.5f, outlineColor, 1.0f)
        } else if (hoverAnim.value > 0.05f) {
            NanoVGRenderer.drawRoundedOutline(pillX, pillY, pillW, pillH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.06f * hoverAnim.value), 0.75f)
        }

        val textColor = if (isListening) NanoVGColor.WHITE else NanoVGColor(0.90f, 0.92f, 0.96f, 0.95f)
        NanoVGRenderer.drawCenteredString(
            bindText,
            pillX + pillW * 0.5f,
            pillY + pillH * 0.5f,
            textColor,
            9f
        )
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isMouseOver(mouseX, mouseY) && button == 0) {
            isListening = !isListening
            return true
        } else if (isListening) {
            isListening = false
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isListening) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                module.keyCode = GLFW.GLFW_KEY_UNKNOWN
            } else {
                module.keyCode = keyCode
            }
            isListening = false
            return true
        }
        return false
    }

    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT"
            GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT"
            GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL"
            GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL"
            GLFW.GLFW_KEY_RIGHT_ALT -> "RALT"
            GLFW.GLFW_KEY_LEFT_ALT -> "LALT"
            GLFW.GLFW_KEY_TAB -> "TAB"
            GLFW.GLFW_KEY_SPACE -> "SPC"
            GLFW.GLFW_KEY_ENTER -> "RET"
            GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS"
            else -> {
                val str = GLFW.glfwGetKeyName(keyCode, 0)
                str?.uppercase() ?: "K$keyCode"
            }
        }
    }
}
