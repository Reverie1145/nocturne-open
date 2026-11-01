package cn.reverie.module.render

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import org.lwjgl.glfw.GLFW

object Fullbright : Module(
    name = "Fullbright",
    description = "Brighten up the dark areas and caves",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting("Mode", "Lighting enhancement mode", "Gamma", listOf("Gamma", "NightVision", "Both"))
    val brightness = NumberSetting("Brightness", "Gamma boost level", 10.0, 1.0, 20.0, 0.5, { mode.isMode("Gamma") || mode.isMode("Both") })
    val removeDarkness = BooleanSetting("Remove Darkness", "Disable Warden Darkness and Blindness darkening", true)

    @JvmField
    var needsUpdate: Boolean = false

    init {
        mode.onChanged = { needsUpdate = true }
        brightness.onChanged = { needsUpdate = true }
        removeDarkness.onChanged = { needsUpdate = true }
    }

    override val suffix: String
        get() = mode.value

    override fun onEnable() {
        needsUpdate = true
    }

    override fun onDisable() {
        needsUpdate = true
    }
}
