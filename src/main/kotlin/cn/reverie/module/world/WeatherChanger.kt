package cn.reverie.module.world

import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object WeatherChanger : Module(
    name = "WeatherChanger",
    description = "Modify client-side weather conditions",
    category = Category.WORLD,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting(
        "Mode",
        "Target weather condition",
        "Clear",
        listOf("Clear", "Rain", "Thunder")
    )

    val rainStrength = NumberSetting(
        "Rain Strength",
        "Precipitation intensity",
        1.0,
        0.1,
        1.0,
        0.1,
        { !mode.isMode("Clear") }
    )

    val thunderStrength = NumberSetting(
        "Thunder Strength",
        "Thunderstorm intensity",
        1.0,
        0.1,
        1.0,
        0.1,
        { mode.isMode("Thunder") }
    )

    init {
        mode.onChanged = { invalidateCache() }
        rainStrength.onChanged = { invalidateCache() }
        thunderStrength.onChanged = { invalidateCache() }
    }

    override val suffix: String
        get() = mode.value

    override fun onEnable() {
        invalidateCache()
    }

    override fun onDisable() {
        invalidateCache()
    }

    private fun invalidateCache() {
        Minecraft.getInstance().level?.environmentAttributes()?.invalidateTickCache()
    }

    fun getEffectiveRainLevel(): Float {
        return when (mode.value) {
            "Clear" -> 0.0f
            "Rain", "Thunder" -> rainStrength.getFloat()
            else -> 0.0f
        }
    }

    fun getEffectiveThunderLevel(): Float {
        return when (mode.value) {
            "Thunder" -> thunderStrength.getFloat()
            else -> 0.0f
        }
    }
}
