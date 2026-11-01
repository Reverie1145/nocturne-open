package cn.reverie.render.ui.screens

import cn.reverie.render.config.RenderConfig
import cn.reverie.render.ui.ModernScreen
import net.minecraft.network.chat.Component
import cn.reverie.render.ui.components.ButtonWidget
import cn.reverie.render.ui.components.CardWidget
import cn.reverie.render.ui.components.DropdownWidget
import cn.reverie.render.ui.components.SliderWidget
import cn.reverie.render.ui.components.TabPanel
import cn.reverie.render.ui.components.ToggleSwitch

class SettingsScreen : ModernScreen(Component.literal("Settings")) {

    override fun buildUI() {
        val card = CardWidget("Graphics & Performance", 3, theme)
        card.width = 320f
        card.height = 240f
        card.x = (width - card.width) * 0.5f
        card.y = (height - card.height) * 0.5f

        val blurToggle = ToggleSwitch("Enable Backdrop Blur", RenderConfig.enableBlur, theme)
        blurToggle.x = 16f
        blurToggle.y = 44f
        blurToggle.width = 288f
        blurToggle.onToggled = { RenderConfig.enableBlur = it }
        card.add(blurToggle)

        val glassToggle = ToggleSwitch("Frosted Glassmorphism", RenderConfig.enableGlassmorphism, theme)
        glassToggle.x = 16f
        glassToggle.y = 76f
        glassToggle.width = 288f
        glassToggle.onToggled = { RenderConfig.enableGlassmorphism = it }
        card.add(glassToggle)

        val animToggle = ToggleSwitch("Smooth UI Animations", RenderConfig.enableAnimations, theme)
        animToggle.x = 16f
        animToggle.y = 108f
        animToggle.width = 288f
        animToggle.onToggled = { RenderConfig.enableAnimations = it }
        card.add(animToggle)

        val radiusSlider = SliderWidget("Blur Radius", RenderConfig.globalBlurRadius, 4f, 32f, 1f, theme)
        radiusSlider.x = 16f
        radiusSlider.y = 140f
        radiusSlider.width = 288f
        radiusSlider.onValueChanged = { RenderConfig.globalBlurRadius = it }
        card.add(radiusSlider)

        val doneBtn = ButtonWidget("Done", theme)
        doneBtn.x = 16f
        doneBtn.y = 194f
        doneBtn.width = 288f
        doneBtn.onClick = { onClose() }
        card.add(doneBtn)

        rootContainer.add(card)
    }
}
