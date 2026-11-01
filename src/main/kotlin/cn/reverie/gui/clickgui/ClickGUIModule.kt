package cn.reverie.gui.clickgui

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.ModeSetting
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.window.WindowClickGuiScreen

object ClickGUIModule : Module(
    name = "ClickGUI",
    description = "Opens the Nocturne ClickGUI",
    category = Category.RENDER,
    keyCode = GLFW.GLFW_KEY_RIGHT_SHIFT
) {

    val panelWidth = NumberSetting("PanelWidth", "Width of each category panel in pixels", 140.0, 110.0, 200.0, 5.0)
    val panelSpacing = NumberSetting("Spacing", "Horizontal gap between panels", 16.0, 6.0, 36.0, 2.0)
    val cornerRadius = NumberSetting("CornerRadius", "Roundness of panels and controls", 7.0, 2.0, 14.0, 1.0)
    val headerHeight = NumberSetting("HeaderHeight", "Height of category title bar", 32.0, 24.0, 44.0, 2.0)
    val moduleHeight = NumberSetting("ModuleHeight", "Base height of module buttons", 24.0, 20.0, 36.0, 2.0)

    val animSpeed = NumberSetting("AnimSpeed", "Animation speed multiplier", 1.0, 0.5, 3.0, 0.1)
    val style = ModeSetting("Style", "ClickGUI layout", "Dropdown", listOf("Dropdown", "Window"))
    private var isStyleSwitching = false

    init {
        addSetting(panelWidth)
        addSetting(panelSpacing)
        addSetting(cornerRadius)
        addSetting(headerHeight)
        addSetting(moduleHeight)

        addSetting(animSpeed)
        addSetting(style)
        style.onChanged = { selected ->
            if (!isStyleSwitching) {
                val mc = Minecraft.getInstance()
                mc.execute {
                    val screen = mc.gui.screen()
                    if (screen is ClickGuiScreen || screen is WindowClickGuiScreen) {
                        if ((selected == "Window") != (screen is WindowClickGuiScreen)) switchStyle(selected)
                    }
                }
            }
        }
    }

    override fun onEnable() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val current = mc.gui.screen()
            if (current is ClickGuiScreen) {
                if (current.isClosing) {
                    current.isClosing = false
                    val animMultiplier = animSpeed.getFloat()
                    current.openAnim.durationMs = (220L / animMultiplier).toLong().coerceIn(60L, 400L)
                    current.openAnim.animateTo(1f)
                }
            } else if (current is WindowClickGuiScreen) {
                current.reopen()
            } else if (current == null) {
                mc.gui.setScreen(if (style.isMode("Window")) WindowClickGuiScreen() else ClickGuiScreen())
            }
        }
    }

    override fun onDisable() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val current = mc.gui.screen()
            if (current is ClickGuiScreen && !current.isClosing) {
                current.startClose()
            } else if (current is WindowClickGuiScreen && !current.isClosing) {
                current.startClose()
            }
        }
    }

    fun switchStyle(nextStyle: String) {
        val mc = Minecraft.getInstance()
        val current = mc.gui.screen()
        if (style.isMode(nextStyle) &&
            ((nextStyle == "Window" && current is WindowClickGuiScreen) ||
                (nextStyle == "Dropdown" && current is ClickGuiScreen))) return
        isStyleSwitching = true
        try {
            if (current is ClickGuiScreen) current.isSwitchingStyle = true
            if (current is WindowClickGuiScreen) current.isSwitchingStyle = true
            style.value = nextStyle
            mc.gui.setScreen(if (style.isMode("Window")) WindowClickGuiScreen() else ClickGuiScreen())
        } finally {
            isStyleSwitching = false
        }
    }
}
