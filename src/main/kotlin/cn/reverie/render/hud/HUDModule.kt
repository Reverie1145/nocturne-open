package cn.reverie.render.hud

import cn.reverie.app.config.ConfigManager
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ColorSetting
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.theme.HUDThemePreset
import cn.reverie.render.theme.Theme
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen

object HUDModule : Module(
    name = "HUD",
    description = "Global HUD theme & visual settings",
    category = Category.RENDER
) {

    val colorMode = ModeSetting(
        "Color Mode",
        "HUD color style and gradient behavior",
        "Theme",
        listOf("Theme", "Static", "Gradient", "Fade", "Wave", "Pulse", "Astolfo", "Rainbow", "Monochrome")
    )
    val themePreset = ModeSetting(
        "Theme",
        "Curated visual theme preset",
        "Nocturne",
        HUDThemePreset.names,
        visibility = { colorMode.isMode("Theme") }
    )

    val fontChoice = ModeSetting(
        "Font",
        "Global typography font for HUD elements and visual module labels",
        "Default",
        cn.reverie.render.font.FontPresets.FONT_NAMES
    )

    val color = ColorSetting(
        "Color",
        "Primary HUD accent color",
        NanoVGColor.fromHex("#7B61FF"),
        visibility = {
            colorMode.isMode("Static") || colorMode.isMode("Gradient") || colorMode.isMode("Fade") ||
            colorMode.isMode("Wave") || colorMode.isMode("Pulse")
        }
    )
    val color2 = ColorSetting(
        "Color 2",
        "Secondary HUD accent color",
        NanoVGColor.fromHex("#5E6AD2"),
        visibility = { colorMode.isMode("Gradient") || colorMode.isMode("Fade") || colorMode.isMode("Wave") }
    )

    val colorSpeed = NumberSetting(
        "Color Speed",
        "Color cycle animation speed",
        1.0,
        0.1,
        4.0,
        0.1,
        visibility = { !colorMode.isMode("Static") && !colorMode.isMode("Monochrome") }
    )
    val colorSpread = NumberSetting(
        "Color Spread",
        "Color dispersion across list items, widgets, and Fade gradients",
        1.0,
        0.1,
        3.0,
        0.1,
        visibility = { !colorMode.isMode("Static") && !colorMode.isMode("Monochrome") }
    )

    val showCrosshair = BooleanSetting("Crosshair", "Show custom vector crosshair", true)

    val bgOpacity = NumberSetting("Bg Opacity", "Shared opacity for HUD widget and ClickGUI panel backgrounds", 0.20, 0.0, 1.0, 0.05)
    val visualAlpha = NumberSetting("Visual Alpha", "Global opacity multiplier for visual effects", 1.0, 0.0, 1.0, 0.05)
    val textShadow = BooleanSetting("Text Shadow", "Toggle text shadows across visual modules", true)

    val widgetShadow = BooleanSetting("Shadow", "Global outer shadow toggle for visual modules", true)
    val shadowMode = ModeSetting(
        "Shadow Mode",
        "Visual module shadow color mode",
        "Default",
        listOf("Default", "Color"),
        { widgetShadow.value }
    )
    val shadowStrength = NumberSetting("Shadow Strength", "Global visual shadow opacity multiplier", 1.0, 0.0, 2.0, 0.05, { widgetShadow.value })

    val snapping = BooleanSetting("Snapping", "Enable magnetic snapping when dragging widgets", true)
    val snapDistance = NumberSetting("SnapDistance", "Magnetic snapping threshold in pixels", 8.0, 2.0, 20.0, 1.0, { snapping.value })

    val blur = BooleanSetting("Blur", "Global frosted glass backdrop blur for visual modules", false)
    val blurRadius = NumberSetting("Blur Radius", "Global backdrop blur intensity in pixels", 12.0, 2.0, 28.0, 1.0, { blur.value })

    val crosshair = CrosshairRenderer()

    init {
        enabled = true

        addSetting(colorMode)
        addSetting(themePreset)
        addSetting(fontChoice)
        addSetting(color)
        addSetting(color2)
        addSetting(colorSpeed)
        addSetting(colorSpread)

        addSetting(showCrosshair)

        addSetting(bgOpacity)
        addSetting(visualAlpha)
        addSetting(textShadow)

        addSetting(widgetShadow)
        addSetting(shadowMode)
        addSetting(shadowStrength)

        addSetting(blur)
        addSetting(blurRadius)

        addSetting(snapping)
        addSetting(snapDistance)
    }

    fun getShadowColor(colorOverride: NanoVGColor? = null, offset: Float = 0f): NanoVGColor {
        return if (shadowMode.isMode("Color")) {
            colorOverride ?: sampleColor(offset)
        } else {
            NanoVGColor(0f, 0f, 0f)
        }
    }

    fun <T> withHudFont(block: () -> T): T {
        return cn.reverie.render.core.RenderEngine.withNamedFont(fontChoice.value, block)
    }

    fun isAnyBlurEnabled(): Boolean {
        return blur.value
    }

    fun getColor1(): NanoVGColor {
        return if (colorMode.isMode("Theme")) {
            HUDThemePreset.fromName(themePreset.value).colors.firstOrNull() ?: color.primaryColor
        } else {
            color.primaryColor
        }
    }

    fun getColor2(): NanoVGColor {
        return if (colorMode.isMode("Theme")) {
            val colors = HUDThemePreset.fromName(themePreset.value).colors
            if (colors.size > 1) colors[1] else color2.primaryColor
        } else {
            color2.primaryColor
        }
    }

    fun interpolateColor(c1: NanoVGColor, c2: NanoVGColor, t: Float): NanoVGColor {
        return Theme.interpolateGamma(c1, c2, t)
    }

    fun sampleFadeColor(offset: Float): NanoVGColor {
        val speed = colorSpeed.getFloat().coerceAtLeast(0.1f)
        val cycleMs = (4200f / speed).toLong().coerceAtLeast(1L)
        val phase = ((1.0 - kotlin.math.cos((System.currentTimeMillis() % cycleMs).toDouble() / cycleMs * Math.PI * 2.0)) * 0.5).toFloat()
        val horizontal = (offset * colorSpread.getFloat()).coerceIn(0f, 1f)
        val start = phase
        val end = 1f - phase
        return interpolateColor(getColor1(), getColor2(), start + (end - start) * horizontal)
    }

    fun sampleColor(offset: Float = 0f): NanoVGColor {
        val speed = colorSpeed.getFloat()
        val spread = colorSpread.getFloat()
        val effectiveOffset = offset * spread

        return when (colorMode.value) {
            "Theme" -> {
                val preset = HUDThemePreset.fromName(themePreset.value)
                preset.sample(effectiveOffset, speed)
            }
            "Astolfo" -> Theme.astolfo(effectiveOffset, speed)
            "Pulse" -> Theme.pulse(getColor1(), effectiveOffset, speed)
            "Rainbow" -> Theme.rainbow(20, 0, effectiveOffset)
            "Monochrome" -> NanoVGColor(1f, 1f, 1f, 0.95f)
            "Static" -> getColor1()
            "Fade" -> sampleFadeColor(offset)
            "Gradient" -> {
                val cycleMs = (3500L / speed.coerceAtLeast(0.1f)).toLong()
                val tRaw = ((System.currentTimeMillis() % cycleMs).toFloat() / cycleMs.toFloat() + effectiveOffset) % 1.0f
                val t = if (tRaw < 0f) tRaw + 1.0f else tRaw
                interpolateColor(getColor1(), getColor2(), t)
            }
            "Wave" -> {
                val cycleMs = (3600L / speed.coerceAtLeast(0.1f)).toLong()
                val progressRaw = ((System.currentTimeMillis() + (effectiveOffset * 800f).toLong()) % cycleMs).toFloat() / (cycleMs * 0.5f)
                val waveProgress = if (progressRaw > 1f) 2f - progressRaw else progressRaw
                val smoothT = ((1.0 - kotlin.math.cos(waveProgress.toDouble() * Math.PI)) / 2.0).toFloat()
                interpolateColor(getColor1(), getColor2(), smoothT)
            }
            else -> Theme.getClientColor(0, timeOffset = (effectiveOffset * 200L).toLong())
        }
    }

    fun openEditor() {
        val mc = Minecraft.getInstance()
        mc.execute {
            // Opening the editor from the chat-bar HUD button should actually enable
            // magnetic alignment, even when an older profile had the setting disabled.
            if (!snapping.value) {
                snapping.value = true
                ConfigManager.markDirty()
            }
            mc.gui.setScreen(ChatScreen("", false))
        }
    }
}
