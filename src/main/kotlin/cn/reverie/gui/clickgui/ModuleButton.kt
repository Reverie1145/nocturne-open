package cn.reverie.gui.clickgui

import cn.reverie.module.*
import cn.reverie.module.player.BedDefender
import cn.reverie.module.player.InvManager
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import net.minecraft.client.Minecraft
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.beddefender.BedDefenderBlockPickerComponent
import cn.reverie.gui.clickgui.beddefender.BedDefenderPreviewComponent
import cn.reverie.gui.clickgui.setting.BooleanSettingComponent
import cn.reverie.gui.clickgui.setting.ColorSettingComponent
import cn.reverie.gui.clickgui.setting.InventorySlotLayoutComponent
import cn.reverie.gui.clickgui.setting.KeybindSettingComponent
import cn.reverie.gui.clickgui.setting.ModeSettingComponent
import cn.reverie.gui.clickgui.setting.ModuleKeybindComponent
import cn.reverie.gui.clickgui.setting.SettingComponent
import cn.reverie.gui.clickgui.setting.SliderSettingComponent
import cn.reverie.gui.clickgui.setting.StringSettingComponent

class ModuleButton(val module: Module) {

    var x: Float = 0f
    var y: Float = 0f
    var width: Float = 140f
        set(value) {
            field = value
            settingComponents.forEach { it.width = value - 8f }
        }

    val baseHeight: Float
        get() = ClickGUIModule.moduleHeight.getFloat()

    var isExpanded: Boolean = expandedMemory[module] ?: false
        set(value) {
            field = value
            expandedMemory[module] = value
        }
    val settingComponents = mutableListOf<SettingComponent>()
    var isBindingKey: Boolean = false
    private var sharePickerOpen = false
    private var hoverStartTime: Long = 0L

    init {
        if (module === BedDefender) settingComponents.add(BedDefenderPreviewComponent())
        for (setting in module.settings) {
            if (setting == module.hidden) continue
            if (module === BedDefender && (setting === BedDefender.height || setting === BedDefender.topCover)) continue
            if (module === BedDefender && setting === BedDefender.customBlock) {
                settingComponents.add(BedDefenderBlockPickerComponent())
                continue
            }
            if (module === InvManager && setting === InvManager.slot1) {
                settingComponents.add(InventorySlotLayoutComponent())
                continue
            }
            if (module === InvManager && InvManager.slotSettings.any { it === setting }) continue
            when (setting) {
                is BooleanSetting -> settingComponents.add(BooleanSettingComponent(setting))
                is NumberSetting -> settingComponents.add(SliderSettingComponent(setting))
                is ModeSetting -> settingComponents.add(ModeSettingComponent(setting))
                is StringSetting -> settingComponents.add(StringSettingComponent(setting))
                is ColorSetting -> settingComponents.add(ColorSettingComponent(setting))
                is BindSetting -> settingComponents.add(KeybindSettingComponent(setting))
            }
        }

        settingComponents.add(ModuleKeybindComponent(module))

        settingComponents.add(BooleanSettingComponent(module.hidden, isMetaSetting = true))
    }

    val toggleAnim = FloatAnimation(if (module.enabled) 1f else 0f, if (module.enabled) 1f else 0f, 260L, CubicBezier.EASE_OUT)
    val hoverAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    val expandAnim = FloatAnimation(
        if (isExpanded) 1f else 0f,
        if (isExpanded) 1f else 0f,
        320L,
        CubicBezier.EASE_IN_OUT
    )
    val sharePickerAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)

    companion object {
        const val DRAWER_PAD_TOP = 4.0f
        const val DRAWER_PAD_BOTTOM = 5.0f
        const val COMP_GAP = 2.5f

        private val expandedMemory = mutableMapOf<Module, Boolean>()
    }

    val contentSettingsHeight: Float
        get() {
            val visibleComps = settingComponents.filter { it.visibilityAnim.value > 0.005f }
            if (visibleComps.isEmpty()) return 0f
            return DRAWER_PAD_TOP + visibleComps.sumOf { (it.height * it.visibilityAnim.value + COMP_GAP * it.visibilityAnim.value).toDouble() }.toFloat() + DRAWER_PAD_BOTTOM + 22f + 62f * sharePickerAnim.value
        }

    val totalHeight: Float
        get() = baseHeight + contentSettingsHeight * expandAnim.value

    fun isMouseOverHeader(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + baseHeight
    }

    fun isMouseOverTotal(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalHeight
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        toggleAnim.durationMs = (260L / animMultiplier).toLong().coerceIn(60L, 800L)
        hoverAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(40L, 400L)
        expandAnim.durationMs = (320L / animMultiplier).toLong().coerceIn(80L, 800L)
        sharePickerAnim.durationMs = (200L / animMultiplier).toLong().coerceIn(60L, 500L)

        val hovered = isMouseOverHeader(mouseX, mouseY)
        val cardHovered = if (isExpanded) isMouseOverTotal(mouseX, mouseY) else hovered
        toggleAnim.animateTo(if (module.enabled) 1f else 0f)
        hoverAnim.animateTo(if (cardHovered) 1f else 0f)
        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        sharePickerAnim.animateTo(if (sharePickerOpen) 1f else 0f)

        if (hovered) {
            if (hoverStartTime == 0L) hoverStartTime = System.currentTimeMillis()
            if (System.currentTimeMillis() - hoverStartTime > 400L && module.description.isNotBlank()) {
                ClickGuiScreen.registerTooltip(module.name, module.description, mouseX, mouseY)
            }
        } else {
            hoverStartTime = 0L
        }

        val theme = HUDModule.sampleColor(0f)
        val btnRadius = 4f

        val idleBg = NanoVGColor(1f, 1f, 1f, 0.02f)
        val hoverBg = NanoVGColor(1f, 1f, 1f, 0.065f)
        val activeBg = NanoVGColor(1f, 1f, 1f, 0.09f)

        val currentBg = idleBg.lerp(hoverBg, hoverAnim.value).lerp(activeBg, toggleAnim.value)
        val totalCardH = totalHeight - 2f
        NanoVGRenderer.drawRoundedRect(x + 2f, y + 1f, width - 4f, totalCardH, btnRadius, currentBg)

        if (hoverAnim.value > 0.02f) {
            NanoVGRenderer.drawRoundedRect(
                x + 2f, y + 1f, width - 4f, totalCardH,
                btnRadius,
                NanoVGColor(1f, 1f, 1f, 0.035f * hoverAnim.value)
            )
        }

        var rightW = 4f

        if (settingComponents.isNotEmpty()) {
            val chevronX = x + width - 10f
            val chevronY = y + baseHeight * 0.5f
            val chevronColor = NanoVGColor(0.50f, 0.52f, 0.60f, 0.75f).lerp(NanoVGColor.WHITE, hoverAnim.value * 0.4f)
            CategoryVectorIcons.drawChevronRight(chevronX, chevronY, 6.5f, expandAnim.value, chevronColor)
            rightW += 12f
        }

        val hasKey = module.keyCode != GLFW.GLFW_KEY_UNKNOWN
        if (hasKey || isBindingKey) {
            val keyStr = if (isBindingKey) "PRESS" else getKeyName(module.keyCode)
            val keyW = NanoVGRenderer.getTextWidth(keyStr, 9f)
            val keyColor = if (isBindingKey) theme else NanoVGColor(0.48f, 0.50f, 0.58f, 0.85f).lerp(NanoVGColor.WHITE, hoverAnim.value * 0.3f)
            NanoVGRenderer.drawRightVerticallyCenteredString(keyStr, x + width - rightW - 2f, y + baseHeight * 0.5f, keyColor, 9f)
            rightW += keyW + 4f
        }

        val inactiveTextColor = NanoVGColor(0.65f, 0.68f, 0.75f, 0.90f)
        val activeTextColor = NanoVGColor(0.98f, 0.98f, 1f, 1f)
        val textColor = inactiveTextColor.lerp(activeTextColor, toggleAnim.value)
        val titleBlur = if (toggleAnim.isRunning) kotlin.math.sin(toggleAnim.value * Math.PI).toFloat() * 3.6f else 0f

        val textStartX = x + 8f
        val maxTitleW = width - (textStartX - x) - rightW - 2f

        NanoVGRenderer.drawMarqueeString(
            module.name,
            textStartX,
            y + baseHeight * 0.5f,
            maxTitleW,
            hovered,
            textColor,
            11f,
            shadow = HUDModule.textShadow.value,
            blur = titleBlur
        )

        if (expandAnim.value > 0.05f) {
            Draw2D.line(
                x + 8f, y + baseHeight,
                x + width - 8f, y + baseHeight,
                NanoVGColor(1f, 1f, 1f, 0.05f * expandAnim.value),
                1.0f
            )
        }

        if (expandAnim.value > 0.01f && settingComponents.isNotEmpty()) {
            val drawerY = y + baseHeight
            val drawerH = contentSettingsHeight * expandAnim.value
            val isExpandingTransition = expandAnim.isRunning && expandAnim.value < 0.995f

            val drawerBlur = if (isExpandingTransition) {
                cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(expandAnim.value, maxBlur = 6.0f)
            } else 0f

            for (comp in settingComponents) {
                comp.updateVisibilityAnim(animMultiplier)
            }

            if (isExpandingTransition) {
                NanoVGRenderer.pushScissor(x + 1f, drawerY - 1.5f, width - 2f, drawerH + 3f)
            }
            try {
                var currentCompY = drawerY + DRAWER_PAD_TOP

                RenderEngine.fontRenderer.withAmbientBlur(drawerBlur) {
                    for (comp in settingComponents) {
                        val vis = comp.visibilityAnim.value
                        if (vis <= 0.005f) continue

                        comp.x = x + 4f
                        comp.y = currentCompY
                        comp.width = width - 8f

                        if (vis < 0.999f) {
                            NanoVGRenderer.withScissor(comp.x, comp.y - 1f, comp.width, comp.height * vis + 2f) {
                                org.lwjgl.nanovg.NanoVG.nvgSave(RenderEngine.nanoVG.handle)
                                org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha(RenderEngine.nanoVG.handle, vis)
                                comp.render(mouseX, mouseY, partialTicks)
                                org.lwjgl.nanovg.NanoVG.nvgRestore(RenderEngine.nanoVG.handle)
                            }
                        } else {
                            comp.render(mouseX, mouseY, partialTicks)
                        }

                        currentCompY += comp.height * vis + COMP_GAP * vis
                    }
                }

                val shareY = currentCompY + 1f
                val shareHover = mouseX in (x + 7f)..(x + width - 7f) && mouseY in shareY..(shareY + 18f)
                NanoVGRenderer.drawRoundedRect(x + 7f, shareY, width - 14f, 18f, 3.5f,
                    NanoVGColor(1f, 1f, 1f, if (shareHover || sharePickerOpen) 0.10f else 0.045f))
                NanoVGRenderer.drawRoundedOutline(x + 7f, shareY, width - 14f, 18f, 3.5f,
                    NanoVGColor(1f, 1f, 1f, 0.10f), 0.7f)
                NanoVGRenderer.drawCenteredString("Share module config", x + width * 0.5f, shareY + 9f,
                    NanoVGColor(0.83f, 0.86f, 0.91f, 0.92f), 7.5f)
                if (sharePickerAnim.value > 0.005f) {
                    val pop = sharePickerAnim.value
                    val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(4)
                    val pickerY = shareY + 16f + 4f * pop
                    val pAlpha = HUDModule.bgOpacity.getFloat() * pop
                    val pBg = NanoVGColor(0.047f, 0.051f, 0.067f, pAlpha)
                    if (HUDModule.widgetShadow.value) {
                        Draw2D.shadow(x + 7f, pickerY, width - 14f, 56f, 4f, 8f, NanoVGColor(0f, 0f, 0f, 0.35f * HUDModule.shadowStrength.getFloat() * pop))
                    }
                    try {
                        RenderEngine.backdropBlurRenderer.renderBackdrop(
                            x + 7f, pickerY, width - 14f, 56f,
                            radius = 4f,
                            tintColor = pBg,
                            blurRadius = HUDModule.blurRadius.getFloat(),
                            enableBlur = HUDModule.blur.value,
                            backdropOpacity = pAlpha
                        )
                    } catch (_: Throwable) {
                        NanoVGRenderer.drawRoundedRect(x + 7f, pickerY, width - 14f, 56f, 4f, pBg)
                    }
                    NanoVGRenderer.drawRoundedOutline(x + 7f, pickerY, width - 14f, 56f, 4f, NanoVGColor(1f, 1f, 1f, 0.08f * pop), 0.75f)
                    if (contacts.isEmpty()) {
                        NanoVGRenderer.drawCenteredString("No online contacts", x + width * 0.5f, pickerY + 28f, NanoVGColor(0.55f, 0.58f, 0.65f, 0.85f * pop), 7.5f)
                    } else {
                        NanoVGRenderer.drawString("Choose an IRC contact", x + 12f, pickerY + 5f, NanoVGColor(0.62f, 0.65f, 0.71f, 0.9f * pop), 6.8f)
                        contacts.forEachIndexed { index, nick ->
                            val rowY = pickerY + 16f + index * 10f
                            val hovered = mouseX in (x + 9f)..(x + width - 9f) && mouseY in rowY..(rowY + 10f)
                            if (hovered) NanoVGRenderer.drawRoundedRect(x + 9f, rowY, width - 18f, 10f, 2f, NanoVGColor(1f, 1f, 1f, 0.08f * pop))
                            NanoVGRenderer.drawVerticallyCenteredString("@$nick", x + 12f, rowY + 5f, NanoVGColor(0.87f, 0.89f, 0.93f, 0.92f * pop), 6.7f)
                        }
                    }
                }

                if (isExpandingTransition && drawerH > 2f) {
                    val overlayAlpha = (1f - expandAnim.value).coerceIn(0f, 1f)

                    val veilColor = NanoVGColor(0.10f, 0.12f, 0.16f, 0.28f * overlayAlpha)
                    NanoVGRenderer.drawRoundedRect(
                        x + 2f, drawerY, width - 4f, drawerH,
                        btnRadius,
                        veilColor
                    )

                    val blurSpread = (drawerBlur * 2.2f).coerceIn(4f, 18f)
                    if (HUDModule.widgetShadow.value) cn.reverie.render.draw.ShadowRenderer.drawShadow(
                        RenderEngine.nanoVG.handle,
                        x + 2f, drawerY, width - 4f, drawerH,
                        btnRadius,
                        blur = blurSpread,
                        spread = 1.0f,
                        color = NanoVGColor(1f, 1f, 1f, 0.22f * overlayAlpha * HUDModule.shadowStrength.getFloat()),
                        offsetY = (1f - expandAnim.value) * 6f
                    )
                }
            } finally {
                if (isExpandingTransition) {
                    NanoVGRenderer.popScissor()
                }
            }

            if (isExpandingTransition && drawerH > 2f) {
                val drawerVy = (1f - expandAnim.value) * 14f
                cn.reverie.render.animation.MotionBlurHelper.renderVerticalMotionBlur(
                    x + 2f, drawerY, width - 4f, drawerH,
                    btnRadius,
                    drawerVy,
                    NanoVGColor(1f, 1f, 1f, 0.28f * (1f - expandAnim.value)),
                    maxBlur = 16f
                )
            }
        }
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isBindingKey) {
            isBindingKey = false
            return true
        }

        if (isMouseOverHeader(mouseX, mouseY)) {
            val isShiftDown = GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                    GLFW.glfwGetKey(Minecraft.getInstance().window.handle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS

            if (button == 0) {
                if (isShiftDown) {
                    isBindingKey = true
                    return true
                }
                module.toggle()
                return true
            } else if (button == 1 && settingComponents.isNotEmpty()) {
                isExpanded = !isExpanded
                return true
            } else if (button == 2) {
                isBindingKey = true
                return true
            }
        }

        if (isExpanded) {
            var currentCompY = y + baseHeight + DRAWER_PAD_TOP
            for (comp in settingComponents) {
                val vis = comp.visibilityAnim.value
                if (vis > 0.005f) currentCompY += comp.height * vis + COMP_GAP * vis
            }
            val shareY = currentCompY + 1f
            if (button == 0 && mouseX in (x + 7f)..(x + width - 7f) && mouseY in shareY..(shareY + 18f)) {
                sharePickerOpen = !sharePickerOpen
                return true
            }
            if (sharePickerOpen) {
                val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(4)
                val pickerY = shareY + 20f
                for ((index, nick) in contacts.withIndex()) {
                    val rowY = pickerY + 16f + index * 10f
                    if (button == 0 && mouseX in (x + 9f)..(x + width - 9f) && mouseY in rowY..(rowY + 10f)) {
                        cn.reverie.nocturne.IrcShareManager.sendModuleConfig(nick, module.name)
                        sharePickerOpen = false
                        return true
                    }
                }
                if (mouseX !in (x + 7f)..(x + width - 7f) || mouseY !in pickerY..(pickerY + 56f)) sharePickerOpen = false
            }
            for (comp in settingComponents) {
                if (comp.isVisible && comp.isMouseOver(mouseX, mouseY)) {
                    if (comp.mouseClicked(mouseX, mouseY, button)) {
                        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
                        for (c in settingComponents) {
                            c.updateVisibilityAnim(animMultiplier)
                        }
                        return true
                    }
                }
            }
        }

        return false
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        var handled = false
        for (comp in settingComponents) {
            if (comp.mouseReleased(mouseX, mouseY, button)) {
                handled = true
            }
        }
        return handled
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isExpanded) {
            for (comp in settingComponents) {
                if (comp.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                    val animMultiplier = ClickGUIModule.animSpeed.getFloat()
                    for (c in settingComponents) {
                        c.updateVisibilityAnim(animMultiplier)
                    }
                    return true
                }
            }
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (isBindingKey) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {

            } else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                module.keyCode = GLFW.GLFW_KEY_UNKNOWN
            } else {
                module.keyCode = keyCode
            }
            isBindingKey = false
            return true
        }

        if (isExpanded) {
            for (comp in settingComponents) {
                if (comp.isVisible && comp.keyPressed(keyCode, scanCode, modifiers)) {
                    return true
                }
            }
        }
        return false
    }

    fun charTyped(codePoint: Int): Boolean {
        if (!isExpanded || codePoint <= 0) return false
        for (comp in settingComponents) {
            if (comp.isVisible && comp.charTyped(codePoint.toChar(), 0)) return true
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
