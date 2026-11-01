package cn.reverie.module.world

import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object TimeChanger : Module(
    name = "TimeChanger",
    description = "Modify client-side world time and day-night cycle",
    category = Category.WORLD,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val mode = ModeSetting(
        "Mode",
        "Time preset or calculation mode",
        "Sunset",
        listOf("Day", "Noon", "Sunset", "Night", "Midnight", "Sunrise", "Custom", "FastCycle")
    )

    val customTime = NumberSetting(
        "Time",
        "Custom time of day in ticks",
        18000.0,
        0.0,
        24000.0,
        100.0,
        { mode.isMode("Custom") }
    )

    val cycleSpeed = NumberSetting(
        "Cycle Speed",
        "Speed of continuous day/night rotation",
        20.0,
        1.0,
        200.0,
        5.0,
        { mode.isMode("FastCycle") }
    )

    init {
        mode.onChanged = { invalidateCache() }
        customTime.onChanged = { invalidateCache() }
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

    fun getEffectiveTime(): Long {
        return when (mode.value) {
            "Day" -> 1000L
            "Noon" -> 6000L
            "Sunset" -> 12000L
            "Night" -> 13000L
            "Midnight" -> 18000L
            "Sunrise" -> 23000L
            "Custom" -> customTime.value.toLong()
            "FastCycle" -> {
                val speed = cycleSpeed.value.toLong().coerceAtLeast(1L)
                ((System.currentTimeMillis() * speed) / 50L) % 24000L
            }
            else -> 6000L
        }
    }
}
