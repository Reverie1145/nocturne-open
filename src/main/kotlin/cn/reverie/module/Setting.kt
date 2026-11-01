package cn.reverie.module

import cn.reverie.app.config.ConfigManager
import cn.reverie.render.nanovg.NanoVGColor

abstract class Setting<T>(
    val name: String,
    val description: String,
    initialValue: T,
    var visibility: () -> Boolean = { true }
) {
    val defaultValue: T = initialValue
    var value: T = initialValue
        set(v) {
            if (field != v) {
                field = v
                ConfigManager.markDirty()
                onChanged?.invoke(v)
            }
        }
    var onChanged: ((T) -> Unit)? = null

    fun reset() {
        value = defaultValue
    }
}

class BooleanSetting(
    name: String,
    description: String,
    value: Boolean,
    visibility: () -> Boolean = { true }
) : Setting<Boolean>(name, description, value, visibility) {
    fun toggle() {
        value = !value
    }
}

class NumberSetting(
    name: String,
    description: String,
    value: Double,
    val min: Double,
    val max: Double,
    val step: Double = 0.1,
    visibility: () -> Boolean = { true }
) : Setting<Double>(name, description, value, visibility) {

    fun getFloat(): Float = value.toFloat()
    fun getInt(): Int = value.toInt()
    fun getDouble(): Double = value

    fun setClamped(newValue: Double) {
        val clamped = newValue.coerceIn(min, max)
        val stepped = if (step > 0.0) {
            Math.round((clamped - min) / step) * step + min
        } else clamped
        value = stepped.coerceIn(min, max)
    }
}

class ModeSetting(
    name: String,
    description: String,
    value: String,
    val modes: List<String>,
    visibility: () -> Boolean = { true }
) : Setting<String>(name, description, value, visibility) {

    init {
        require(modes.contains(value)) { "Default value '$value' must exist in modes list $modes" }
    }

    fun isMode(mode: String): Boolean = value.equals(mode, ignoreCase = true)
    fun isCurrent(mode: String): Boolean = isMode(mode)

    fun cycleNext() {
        val index = modes.indexOf(value)
        val nextIndex = (index + 1) % modes.size
        value = modes[nextIndex]
    }

    fun cyclePrev() {
        val index = modes.indexOf(value)
        val prevIndex = if (index <= 0) modes.size - 1 else index - 1
        value = modes[prevIndex]
    }
}

class StringSetting(
    name: String,
    description: String,
    value: String,
    visibility: () -> Boolean = { true }
) : Setting<String>(name, description, value, visibility)

class BindSetting(
    name: String,
    description: String,
    value: Int = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN,
    visibility: () -> Boolean = { true }
) : Setting<Int>(name, description, value, visibility) {
    fun isBound(): Boolean = value != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
}

enum class ColorMode(val displayName: String) {
    STATIC("Static"),
    GRADIENT_2("2-Gradient"),
    GRADIENT_3("3-Gradient"),
    GRADIENT_4("4-Gradient"),
    RAINBOW("Rainbow")
}

class ColorSetting(
    name: String,
    description: String,
    value: NanoVGColor = NanoVGColor(0.38f, 0.44f, 0.95f, 0.85f),
    colorMode: ColorMode = ColorMode.STATIC,
    visibility: () -> Boolean = { true }
) : Setting<NanoVGColor>(name, description, value, visibility) {

    var colorMode: ColorMode = colorMode
        set(v) {
            if (field != v) {
                field = v
                ConfigManager.markDirty()
            }
        }

    val colorStops = mutableListOf(
        value,
        value.lerp(NanoVGColor(0.20f, 0.75f, 0.95f, value.a), 0.7f),
        value.lerp(NanoVGColor(0.95f, 0.35f, 0.75f, value.a), 0.8f),
        value.lerp(NanoVGColor(0.25f, 0.85f, 0.55f, value.a), 0.8f)
    )

    var activeStopIndex: Int = 0
    var flowSpeed: Float = 1.0f
        set(v) {
            if (field != v) {
                field = v
                ConfigManager.markDirty()
            }
        }

    val primaryColor: NanoVGColor
        get() = value

    val activeColor: NanoVGColor
        get() {
            val index = activeStopIndex.coerceIn(0, colorStops.size - 1)
            return if (index == 0) primaryColor else colorStops[index]
        }

    fun setPrimaryColor(c: NanoVGColor) {
        if (colorStops.isEmpty()) colorStops.add(c) else colorStops[0] = c
        if (value != c) {
            value = c
        } else {
            ConfigManager.markDirty()
        }
    }

    fun setActiveColor(c: NanoVGColor) {
        val idx = activeStopIndex.coerceIn(0, colorStops.size - 1)
        colorStops[idx] = c
        if (idx == 0) {
            setPrimaryColor(c)
        } else {
            ConfigManager.markDirty()
        }
    }

    fun sampleColor(offset: Float = 0f): NanoVGColor {
        return when (colorMode) {
            ColorMode.STATIC -> primaryColor
            ColorMode.RAINBOW -> {
                val time = (System.currentTimeMillis() * 0.0008f * flowSpeed + offset) % 1.0f
                val h = (time * 360f) % 360f
                NanoVGColor.fromHsv(h, 0.85f, 0.95f, primaryColor.a)
            }
            ColorMode.GRADIENT_2 -> {
                val c1 = primaryColor
                val c2 = colorStops.getOrElse(1) { c1 }
                val time = (System.currentTimeMillis() * 0.001f * flowSpeed + offset) % 1.0f
                val t = (Math.sin(time * Math.PI * 2.0) + 1.0).toFloat() * 0.5f
                c1.lerp(c2, t)
            }
            ColorMode.GRADIENT_3 -> {
                val list = listOf(primaryColor, colorStops[1], colorStops[2])
                sampleMultiStop(list, offset)
            }
            ColorMode.GRADIENT_4 -> {
                val list = listOf(primaryColor, colorStops[1], colorStops[2], colorStops[3])
                sampleMultiStop(list, offset)
            }
        }
    }

    private fun sampleMultiStop(stops: List<NanoVGColor>, offset: Float): NanoVGColor {
        val n = stops.size
        if (n <= 1) return stops[0]
        val time = ((System.currentTimeMillis() * 0.0008f * flowSpeed + offset) % 1.0f + 1.0f) % 1.0f
        val pos = time * n
        val i1 = pos.toInt() % n
        val i2 = (i1 + 1) % n
        val factor = pos - pos.toInt()
        return stops[i1].lerp(stops[i2], factor)
    }

    fun getColorPair(): Pair<NanoVGColor, NanoVGColor> {
        return when (colorMode) {
            ColorMode.STATIC -> Pair(primaryColor, primaryColor)
            ColorMode.RAINBOW -> Pair(sampleColor(0f), sampleColor(0.35f))
            ColorMode.GRADIENT_2 -> Pair(primaryColor, colorStops[1])
            ColorMode.GRADIENT_3 -> Pair(primaryColor, colorStops[2])
            ColorMode.GRADIENT_4 -> Pair(primaryColor, colorStops[3])
        }
    }
}
