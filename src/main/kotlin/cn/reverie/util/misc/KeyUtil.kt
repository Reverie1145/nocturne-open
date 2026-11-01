package cn.reverie.util.misc

import org.lwjgl.glfw.GLFW
import java.util.Locale

object KeyUtil {

    private val keyToName = mutableMapOf<Int, String>()
    private val nameToKey = mutableMapOf<String, Int>()

    init {
        registerKey(GLFW.GLFW_KEY_UNKNOWN, "NONE")
        registerKey(GLFW.GLFW_KEY_SPACE, "SPACE")
        registerKey(GLFW.GLFW_KEY_APOSTROPHE, "APOSTROPHE")
        registerKey(GLFW.GLFW_KEY_COMMA, "COMMA")
        registerKey(GLFW.GLFW_KEY_MINUS, "MINUS")
        registerKey(GLFW.GLFW_KEY_PERIOD, "PERIOD")
        registerKey(GLFW.GLFW_KEY_SLASH, "SLASH")
        registerKey(GLFW.GLFW_KEY_SEMICOLON, "SEMICOLON")
        registerKey(GLFW.GLFW_KEY_EQUAL, "EQUAL")
        registerKey(GLFW.GLFW_KEY_LEFT_BRACKET, "LBRACKET")
        registerKey(GLFW.GLFW_KEY_BACKSLASH, "BACKSLASH")
        registerKey(GLFW.GLFW_KEY_RIGHT_BRACKET, "RBRACKET")
        registerKey(GLFW.GLFW_KEY_GRAVE_ACCENT, "GRAVE")

        for (i in 0..9) {
            registerKey(GLFW.GLFW_KEY_0 + i, i.toString())
        }

        for (i in 0..25) {
            val char = ('A'.code + i).toChar()
            registerKey(GLFW.GLFW_KEY_A + i, char.toString())
        }

        registerKey(GLFW.GLFW_KEY_ESCAPE, "ESCAPE")
        registerKey(GLFW.GLFW_KEY_ENTER, "ENTER")
        registerKey(GLFW.GLFW_KEY_TAB, "TAB")
        registerKey(GLFW.GLFW_KEY_BACKSPACE, "BACKSPACE")
        registerKey(GLFW.GLFW_KEY_INSERT, "INSERT")
        registerKey(GLFW.GLFW_KEY_DELETE, "DELETE")
        registerKey(GLFW.GLFW_KEY_RIGHT, "RIGHT")
        registerKey(GLFW.GLFW_KEY_LEFT, "LEFT")
        registerKey(GLFW.GLFW_KEY_DOWN, "DOWN")
        registerKey(GLFW.GLFW_KEY_UP, "UP")
        registerKey(GLFW.GLFW_KEY_PAGE_UP, "PAGE_UP")
        registerKey(GLFW.GLFW_KEY_PAGE_DOWN, "PAGE_DOWN")
        registerKey(GLFW.GLFW_KEY_HOME, "HOME")
        registerKey(GLFW.GLFW_KEY_END, "END")
        registerKey(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS")
        registerKey(GLFW.GLFW_KEY_SCROLL_LOCK, "SCROLL_LOCK")
        registerKey(GLFW.GLFW_KEY_NUM_LOCK, "NUM_LOCK")
        registerKey(GLFW.GLFW_KEY_PRINT_SCREEN, "PRINT_SCREEN")
        registerKey(GLFW.GLFW_KEY_PAUSE, "PAUSE")

        for (i in 1..12) {
            registerKey(GLFW.GLFW_KEY_F1 + (i - 1), "F$i")
        }

        registerKey(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT")
        registerKey(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL")
        registerKey(GLFW.GLFW_KEY_LEFT_ALT, "LALT")
        registerKey(GLFW.GLFW_KEY_LEFT_SUPER, "LSUPER")
        registerKey(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT")
        registerKey(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL")
        registerKey(GLFW.GLFW_KEY_RIGHT_ALT, "RALT")
        registerKey(GLFW.GLFW_KEY_RIGHT_SUPER, "RSUPER")
        registerKey(GLFW.GLFW_KEY_MENU, "MENU")

        nameToKey["CLEAR"] = GLFW.GLFW_KEY_UNKNOWN
        nameToKey["NULL"] = GLFW.GLFW_KEY_UNKNOWN
        nameToKey["UNBIND"] = GLFW.GLFW_KEY_UNKNOWN
    }

    private fun registerKey(keyCode: Int, name: String) {
        keyToName[keyCode] = name
        nameToKey[name.uppercase(Locale.ROOT)] = keyCode
    }

    fun getKeyName(keyCode: Int): String {
        return keyToName[keyCode] ?: run {
            val glfwName = GLFW.glfwGetKeyName(keyCode, 0)
            glfwName?.uppercase(Locale.ROOT) ?: "KEY_$keyCode"
        }
    }

    fun getKeyCode(name: String): Int {
        val upper = name.trim().uppercase(Locale.ROOT)
        nameToKey[upper]?.let { return it }

        if (upper.startsWith("KEY_")) {
            upper.removePrefix("KEY_").toIntOrNull()?.let { return it }
        }
        return GLFW.GLFW_KEY_UNKNOWN
    }

    fun getAllKeyNames(): List<String> {
        return keyToName.values.filter { it != "NONE" } + listOf("NONE", "CLEAR")
    }
}
