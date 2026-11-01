package cn.reverie.gui.clickgui

import cn.reverie.module.misc.YuriGallery
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.art.AnimeArt
import cn.reverie.app.config.ConfigManager
import cn.reverie.module.Category
import cn.reverie.module.ModuleManager
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.arcade.NocturneArcadeModal
import cn.reverie.gui.clickgui.arcade.ArcadeSoundHelper
import cn.reverie.gui.clickgui.beddefender.BedDefenderPreviewComponent
import cn.reverie.gui.clickgui.irc.IrcChatPanel
import cn.reverie.gui.clickgui.setting.StringSettingComponent
import cn.reverie.gui.clickgui.window.ConfigProfilePanel
internal data class ClickGuiTooltipInfo(val title: String, val description: String, val mouseX: Float, val mouseY: Float)

class ClickGuiScreen : Screen(Component.literal("Nocturne ClickGUI")) {

    val panels = mutableListOf<CategoryDropdownPanel>()

    var ircOpen = IrcChatPanel.savedOpen ?: true
    val ircPanel = IrcChatPanel(0f, 46f)
    internal val configPanel = ConfigProfilePanel(0f, 46f)

    fun toggleIrcPanel() {
        ircOpen = !ircOpen
        IrcChatPanel.savedOpen = ircOpen
        if (!ircOpen) surfaceFeedback[ircPanel]?.reset()
        if (ircOpen && IrcChatPanel.savedPosition == null) positionIrcPanel()
    }

    fun toggleConfigPanel() {
        configPanel.toggle()
        ConfigProfilePanel.savedOpen = configPanel.isOpen
        if (!configPanel.isOpen) surfaceFeedback[configPanel]?.reset()
        if (configPanel.isOpen && ConfigProfilePanel.savedPosition == null) positionConfigPanel()
    }

    private fun positionConfigPanel() {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val firstPanel = panels.firstOrNull()
        val defaultX = firstPanel?.x ?: 14f
        val maxY = (screenH - configPanel.totalHeight - 10f).coerceAtLeast(46f)
        configPanel.x = defaultX.coerceIn(4f, (screenW - configPanel.width - 4f).coerceAtLeast(4f))
        configPanel.y = 46f.coerceAtMost(maxY)
    }

    private fun positionIrcPanel() {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val spacing = ClickGUIModule.panelSpacing.getFloat()
        val lastPanel = panels.lastOrNull()
        val defaultX = if (lastPanel != null && lastPanel.x + lastPanel.width + spacing + ircPanel.width <= screenW - 10f) {
            lastPanel.x + lastPanel.width + spacing
        } else {
            configPanel.x + configPanel.width + spacing
        }
        val maxY = (screenH - ircPanel.totalHeight - 10f).coerceAtLeast(46f)
        ircPanel.x = defaultX.coerceIn(4f, (screenW - ircPanel.width - 4f).coerceAtLeast(4f))
        ircPanel.y = 46f.coerceAtMost(maxY)
        ircPanel.isExpanded = true
        ircPanel.jumpToBottom()
    }

    val openAnim = FloatAnimation(0f, 1f, 380L, CubicBezier.EASE_OUT)
    private val surfaceFeedback = mutableMapOf<Any, ClickGuiPressFeedback>()
    private val commandBarSurface = Any()
    private var lastCommandBarLayout: ClickGuiCommandBarLayout? = null
    var isClosing = false
    var isSwitchingStyle = false

    var searchText: String = lastSearchText
        set(value) {
            field = value
            lastSearchText = value
        }
    var isSearchFocused: Boolean = false
    internal var cursorTimer: Long = 0L

    val searchFocusAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
    val tooltipFadeAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
    internal var prevBarY: Float = Float.NaN
    var logoClickCount: Int = 0
    var lastLogoClickTime: Long = 0L

    companion object {
        internal var activeTooltip: ClickGuiTooltipInfo? = null
        private val panelPositionMemory = mutableMapOf<Category, Pair<Float, Float>>()
        private var lastSearchText = ""

        fun registerTooltip(title: String, description: String, mouseX: Float, mouseY: Float) {
            activeTooltip = ClickGuiTooltipInfo(title, description, mouseX, mouseY)
        }
    }

    private fun panelSlideOffset(): Float {
        val animVal = openAnim.value
        return if (animVal < 0.999f) (1f - ((animVal - 0.20f) / 0.80f).coerceIn(0f, 1f)) * 18f else 0f
    }

    private fun guiScale(): Float = if (openAnim.value < 0.999f) 0.97f + 0.03f * openAnim.value else 1f

    private fun guiPointer(mx: Float, my: Float): Pair<Float, Float> {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val scale = guiScale()
        val x = screenW * 0.5f + (mx - screenW * 0.5f) / scale
        val y = screenH * 0.5f + (my - screenH * 0.5f) / scale
        return Pair(x, y)
    }

    private fun feedbackFor(surface: Any): ClickGuiPressFeedback =
        surfaceFeedback.getOrPut(surface) { ClickGuiPressFeedback() }

    private fun renderSurface(
        surface: Any, x: Float, y: Float, width: Float, height: Float,
        radius: Float, alpha: Float, mouseX: Float, mouseY: Float, draw: (Float, Float) -> Unit
    ) {
        val feedback = feedbackFor(surface)
        feedback.withSurface(x, y, width, height, radius, alpha) {
            val (mx, my) = feedback.pointer(mouseX, mouseY, x, y, width, height)
            draw(mx, my)
        }
    }

    private fun createCommandBarLayout(screenW: Float): ClickGuiCommandBarLayout {
        val statusText = "${ModuleManager.modules.count { it.enabled }} on  ·  esc"
        return ClickGuiCommandBarLayout(screenW, openAnim.value.coerceIn(0f, 1f), isClosing,
            searchFocusAnim.value.coerceIn(0f, 1f), statusText, NanoVGRenderer.getTextWidth(statusText, 9.5f))
    }

    private fun renderCommandBar(mouseX: Float, mouseY: Float, screenW: Float, theme: NanoVGColor, alpha: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        searchFocusAnim.durationMs = (150L / animMultiplier).toLong().coerceIn(50L, 350L)
        searchFocusAnim.animateTo(if (isSearchFocused) 1f else 0f)
        val layout = createCommandBarLayout(screenW)
        lastCommandBarLayout = layout
        val bounds = layout.bounds
        val (entryX, entryY) = layout.pointer(mouseX, mouseY)
        val transform = RenderEngine.nanoVG.transform
        transform.save()
        try {
            transform.translate(bounds.centerX, bounds.centerY)
            transform.scale(layout.scaleX, layout.scaleY)
            transform.translate(-bounds.centerX, -bounds.centerY)
            renderSurface(commandBarSurface, bounds.x, bounds.y, bounds.width, bounds.height,
                bounds.height * 0.5f, alpha, entryX, entryY) { mx, my ->
                val feedback = feedbackFor(commandBarSurface)
                val overBacking = bounds.contains(entryX, entryY) ||
                    (feedback.compression > 0.005f && bounds.contains(entryX, entryY - 2f))
                if (bounds.contains(mx, my) || overBacking) activeTooltip = null
                renderMinimalCommandBar(mx, my, layout, theme)
            }
        } finally {
            transform.restore()
        }
    }

    override fun init() {
        super.init()
        surfaceFeedback.values.forEach { it.reset() }
        lastCommandBarLayout = null
        prevBarY = Float.NaN
        isClosing = false
        openAnim.setImmediate(0f)
        openAnim.animateTo(1f)
        isSearchFocused = false
        cursorTimer = System.currentTimeMillis()

        if (panels.isEmpty()) {
            restorePanelPositions()
        }
    }

    fun resetPanelPositions() {
        populatePanels(restoreSavedPositions = false)
        positionConfigPanel()
        positionIrcPanel()
    }

    private fun restorePanelPositions() {
        populatePanels(restoreSavedPositions = true)
        val savedCfg = ConfigProfilePanel.savedPosition
        if (savedCfg != null) {
            configPanel.x = savedCfg.first
            configPanel.y = savedCfg.second
        } else {
            positionConfigPanel()
        }
        val savedIrc = IrcChatPanel.savedPosition
        if (savedIrc != null) {
            ircPanel.x = savedIrc.first
            ircPanel.y = savedIrc.second
        } else {
            positionIrcPanel()
        }
    }

    private fun populatePanels(restoreSavedPositions: Boolean) {
        surfaceFeedback.keys.removeAll { it is CategoryDropdownPanel }
        panels.clear()
        val categories = Category.entries.filter { ModuleManager.getModulesByCategory(it).isNotEmpty() }
        val panelWidth = ClickGUIModule.panelWidth.getFloat()
        val spacing = ClickGUIModule.panelSpacing.getFloat()
        val totalWidth = categories.size * panelWidth + (categories.size - 1) * spacing
        val screenW = ResolutionHelper.scaledWidth
        val startX = ((screenW - totalWidth) * 0.5f).coerceAtLeast(10f)
        val startY = 46f

        for ((index, cat) in categories.withIndex()) {
            val defaultX = startX + index * (panelWidth + spacing)
            val savedPosition = if (restoreSavedPositions) panelPositionMemory[cat] else null
            val panelX = savedPosition?.first?.coerceIn(4f, (screenW - panelWidth - 4f).coerceAtLeast(4f)) ?: defaultX
            val panelY = savedPosition?.second?.coerceIn(4f, (ResolutionHelper.scaledHeight - ClickGUIModule.headerHeight.getFloat() - 4f).coerceAtLeast(4f)) ?: startY
            val panel = CategoryDropdownPanel(cat, panelX, panelY, panelWidth)
            panels.add(panel)
            panelPositionMemory[cat] = Pair(panelX, panelY)
        }
    }

    private fun persistUiState() {
        lastSearchText = searchText
        panels.forEach { panel ->
            panelPositionMemory[panel.category] = Pair(panel.x, panel.y)
            CategoryDropdownPanel.scrollMemory[panel.category] = panel.targetScroll
            CategoryDropdownPanel.expandMemory[panel.category] = panel.isExpanded
        }
        ConfigProfilePanel.savedPosition = Pair(configPanel.x, configPanel.y)
        ConfigProfilePanel.savedExpanded = configPanel.isExpanded
        ConfigProfilePanel.savedScroll = configPanel.targetScroll
        ConfigProfilePanel.savedOpen = configPanel.isOpen

        IrcChatPanel.savedPosition = Pair(ircPanel.x, ircPanel.y)
        IrcChatPanel.savedExpanded = ircPanel.isExpanded
        IrcChatPanel.savedScroll = ircPanel.targetScroll
        IrcChatPanel.savedOpen = ircOpen
    }

    fun startClose() {
        if (isClosing) return
        isClosing = true
        surfaceFeedback.values.forEach { it.releaseAll() }
        BedDefenderPreviewComponent.clearFocus()
        StringSettingComponent.clearFocus()
        isSearchFocused = false
        NocturneArcadeModal.close()
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        openAnim.durationMs = (180L / animMultiplier).toLong().coerceIn(60L, 400L)
        openAnim.animateTo(0f)
        if (ClickGUIModule.enabled) {
            ClickGUIModule.enabled = false
        }
        persistUiState()
        ConfigManager.flush()
    }

    override fun removed() {
        super.removed()
        BedDefenderPreviewComponent.clearFocus()
        StringSettingComponent.clearFocus()
        NocturneArcadeModal.close()
        persistUiState()
        if (isSwitchingStyle) return
        if (ClickGUIModule.enabled) {
            ClickGUIModule.enabled = false
        }
        ConfigManager.flush()
    }

    override fun onClose() {
        startClose()
    }

    override fun isPauseScreen(): Boolean = false

    fun renderClickGui(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (isClosing && (openAnim.isFinished || openAnim.value <= 0.02f)) {
            Minecraft.getInstance().gui.setScreen(null)
            return
        }

        activeTooltip = null
        val animVal = openAnim.value
        val theme = HUDModule.sampleColor(0f)

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight

        val dimAlpha = 0.35f * animVal
        if (dimAlpha > 0.005f) {
            NanoVGRenderer.drawRoundedRect(
                0f, 0f, screenW, screenH, 0f,
                NanoVGColor(0f, 0f, 0f, dimAlpha)
            )
        }

        if (YuriGallery.enabled && YuriGallery.guiArt.value) {
            val bob = AnimeArt.idleBob(System.currentTimeMillis())
            val chibiH = (screenH * 0.17f).coerceIn(92f, 150f)
            val chibiAlpha = 0.92f * animVal
            if (HUDModule.widgetShadow.value) Draw2D.shadow(screenW - chibiH * 0.62f - 26f, screenH - 9f, chibiH * 0.62f, 7f, 3.5f, 7f, NanoVGColor(0f, 0f, 0f, 0.30f * animVal * HUDModule.shadowStrength.getFloat()), 1f)
            AnimeArt.drawStanding(AnimeArt.GUI_CHIBI_RIGHT, screenW - chibiH * 0.31f - 26f, screenH - 10f + bob * 1.5f, chibiH, alpha = chibiAlpha)
            if (HUDModule.widgetShadow.value) Draw2D.shadow(26f, screenH - 9f, chibiH * 0.62f, 7f, 3.5f, 7f, NanoVGColor(0f, 0f, 0f, 0.30f * animVal * HUDModule.shadowStrength.getFloat()), 1f)
            AnimeArt.drawStanding(AnimeArt.GUI_CHIBI_LEFT, 26f + chibiH * 0.31f, screenH - 10f - bob * 1.5f, chibiH, alpha = chibiAlpha)
        }

        val nvg = RenderEngine.nanoVG
        nvg.transform.save()
        if (animVal < 0.999f) {
            val cx = screenW * 0.5f
            val cy = screenH * 0.5f
            val scale = 0.97f + 0.03f * animVal
            nvg.transform.translate(cx, cy)
            nvg.transform.scale(scale, scale)
            nvg.transform.translate(-cx, -cy)
        }

        val (guiMouseX, guiMouseY) = guiPointer(mouseX, mouseY)
        val panelSlideY = panelSlideOffset()
        if (panelSlideY > 0.01f) {
            nvg.transform.save()
            nvg.transform.translate(0f, panelSlideY)
        }
        for (panel in panels) {
            renderSurface(panel, panel.x, panel.y, panel.width, panel.totalHeight,
                ClickGUIModule.cornerRadius.getFloat(), animVal, guiMouseX, guiMouseY - panelSlideY) { mx, my ->
                panel.render(mx, my, partialTicks, searchText.trim())
            }
            panelPositionMemory[panel.category] = Pair(panel.x, panel.y)
        }
        if (configPanel.isOpen) {
            renderSurface(configPanel, configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight,
                ClickGUIModule.cornerRadius.getFloat(), animVal, guiMouseX, guiMouseY - panelSlideY) { mx, my ->
                configPanel.render(mx, my, partialTicks)
            }
        }
        if (ircOpen) {
            renderSurface(ircPanel, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight,
                ClickGUIModule.cornerRadius.getFloat(), animVal, guiMouseX, guiMouseY - panelSlideY) { mx, my ->
                ircPanel.render(mx, my, partialTicks)
            }
        }
        if (panelSlideY > 0.01f) {
            nvg.transform.restore()
        }

        val filter = searchText.trim()
        if (YuriGallery.enabled && YuriGallery.guiArt.value && filter.isNotEmpty() &&
            ModuleManager.modules.none { it.name.contains(filter, ignoreCase = true) } && animVal > 0.85f
        ) {
            renderEmptySearchCard(screenW, screenH, animVal)
        }

        renderCommandBar(guiMouseX, guiMouseY, screenW, theme, animVal)

        if (NocturneArcadeModal.isOpen) {
            NocturneArcadeModal.render(guiMouseX, guiMouseY, partialTicks)
        }

        val tooltip = activeTooltip?.copy(mouseX = guiMouseX, mouseY = guiMouseY)
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        tooltipFadeAnim.durationMs = (140L / animMultiplier).toLong().coerceIn(50L, 350L)
        if (tooltip != null && animVal > 0.85f) {
            tooltipFadeAnim.animateTo(1f)
        } else {
            tooltipFadeAnim.animateTo(0f)
        }

        if (tooltipFadeAnim.value > 0.02f && tooltip != null) {
            renderTooltipOverlay(tooltip, screenW, screenH, tooltipFadeAnim.value)
        }

        nvg.transform.restore()
    }




    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {}
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        StringSettingComponent.clearFocus()
        BedDefenderPreviewComponent.clearFocus()
        val (guiX, guiY) = guiPointer(
            ResolutionHelper.toCanvasX(event.x().toFloat()), ResolutionHelper.toCanvasY(event.y().toFloat())
        )
        val button = event.button()

        if (NocturneArcadeModal.isOpen) {
            if (NocturneArcadeModal.mouseClicked(guiX, guiY, button)) return true
        }

        val layout = lastCommandBarLayout ?: createCommandBarLayout(ResolutionHelper.scaledWidth)
        val bar = layout.bounds
        val (entryX, entryY) = layout.pointer(guiX, guiY)
        val barFeedback = feedbackFor(commandBarSurface)
        val (mx, my) = barFeedback.pointer(entryX, entryY, bar.x, bar.y, bar.width, bar.height)
        val overBacking = bar.contains(entryX, entryY) ||
            (barFeedback.compression > 0.005f && bar.contains(entryX, entryY - 2f))

        // The exposed backing also consumes clicks while the front is pressed inward.
        if (bar.contains(mx, my) || overBacking) {
            if (!bar.contains(mx, my)) return true
            barFeedback.press(mx, my, bar.x, bar.y, bar.width, bar.height, button)
            if (button != 0) return true

            ircPanel.inputFocused = false
            configPanel.inputFocused = false
            isSearchFocused = layout.search.bounds.contains(mx, my)
            if (isSearchFocused) {
                if (searchText.isNotEmpty() && layout.search.clearButton.contains(mx, my)) searchText = ""
                cursorTimer = System.currentTimeMillis()
            } else when {
                layout.reset.contains(mx, my) -> {
                    val now = System.currentTimeMillis()
                    if (now - lastLogoClickTime < 1600L) {
                        logoClickCount++
                    } else {
                        logoClickCount = 1
                    }
                    lastLogoClickTime = now

                    if (logoClickCount >= 5) {
                        logoClickCount = 0
                        NocturneArcadeModal.open()
                    } else {
                        ArcadeSoundHelper.playComboTick(logoClickCount)
                    }
                }
                layout.config.contains(mx, my) -> toggleConfigPanel()
                layout.irc.contains(mx, my) -> toggleIrcPanel()
                layout.hud.contains(mx, my) -> HUDModule.openEditor()
            }
            return true
        }
        isSearchFocused = false

        if (ircOpen) {
            val feedback = feedbackFor(ircPanel)
            val (px, py) = feedback.pointer(guiX, guiY - panelSlideOffset(),
                ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight)
            val over = ircPanel.isMouseOverTotal(px, py)
            if (over) {
                feedback.press(px, py, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight, button)
                isSearchFocused = false
            }
            if (ircPanel.mouseClicked(px, py, button, doubleClick) || over) {
                return true
            }
            ircPanel.inputFocused = false
        }

        if (configPanel.isOpen) {
            val feedback = feedbackFor(configPanel)
            val (px, py) = feedback.pointer(guiX, guiY - panelSlideOffset(),
                configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight)
            val over = configPanel.isMouseOverTotal(px, py)
            if (over) {
                feedback.press(px, py, configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight, button)
                isSearchFocused = false
            }
            if (configPanel.mouseClicked(px, py, button) || over) return true
            configPanel.inputFocused = false
        }

        for (i in panels.indices.reversed()) {
            val panel = panels[i]
            val feedback = feedbackFor(panel)
            val (px, py) = feedback.pointer(guiX, guiY - panelSlideOffset(), panel.x, panel.y, panel.width, panel.totalHeight)
            if (panel.isMouseOverTotal(px, py)) {
                feedback.press(px, py, panel.x, panel.y, panel.width, panel.totalHeight, button)
                if (panel.mouseClicked(px, py, button)) {
                    if (i != panels.size - 1) {
                        panels.removeAt(i)
                        panels.add(panel)
                    }
                    return true
                }
                return true
            }
        }

        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val button = event.button()
        surfaceFeedback.values.forEach { it.release(button) }
        val (mx, my) = guiPointer(
            ResolutionHelper.toCanvasX(event.x().toFloat()), ResolutionHelper.toCanvasY(event.y().toFloat())
        )
        if (NocturneArcadeModal.isOpen && NocturneArcadeModal.mouseReleased(mx, my, button)) {
            return true
        }
        val py = my - panelSlideOffset()

        val configPoint = feedbackFor(configPanel).pointer(mx, py,
            configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight)
        if (configPanel.mouseReleased(configPoint.first, configPoint.second)) return true
        var handled = false
        if (ircOpen) {
            val point = feedbackFor(ircPanel).pointer(mx, py, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight)
            ircPanel.mouseReleased(point.first, point.second, button)
        }
        for (panel in panels.reversed()) {
            val point = feedbackFor(panel).pointer(mx, py, panel.x, panel.y, panel.width, panel.totalHeight)
            if (panel.mouseReleased(point.first, point.second, button)) {
                handled = true
            }
        }
        return handled || super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val (mx, my) = guiPointer(
            ResolutionHelper.toCanvasX(event.x().toFloat()), ResolutionHelper.toCanvasY(event.y().toFloat())
        )
        val py = my - panelSlideOffset()
        val dragX = ResolutionHelper.toCanvasDelta(dx).toFloat() / guiScale()
        val dragY = ResolutionHelper.toCanvasDelta(dy).toFloat() / guiScale()
        val button = event.button()

        if (NocturneArcadeModal.isOpen && NocturneArcadeModal.mouseDragged(mx, my, button, dragX, dragY)) {
            return true
        }

        val configFeedback = feedbackFor(configPanel)
        if (configPanel.isDragging) configFeedback.release(button)
        val configPoint = configFeedback.pointer(mx, py,
            configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight)
        if (configPanel.mouseDragged(configPoint.first, configPoint.second)) return true

        if (ircOpen) {
            val feedback = feedbackFor(ircPanel)
            if (ircPanel.isDragging) feedback.release(button)
            val point = feedback.pointer(mx, py, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight)
            val delta = feedback.delta(dragX, dragY, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight)
            if (ircPanel.mouseDragged(point.first, point.second, button, delta.first, delta.second)) return true
        }

        for (panel in panels.reversed()) {
            val feedback = feedbackFor(panel)
            if (panel.isDragging) feedback.release(button)
            val point = feedback.pointer(mx, py, panel.x, panel.y, panel.width, panel.totalHeight)
            val delta = feedback.delta(dragX, dragY, panel.x, panel.y, panel.width, panel.totalHeight)
            if (panel.mouseDragged(point.first, point.second, button, delta.first, delta.second)) {
                return true
            }
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val (mx, my) = guiPointer(ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat())
        val py = my - panelSlideOffset()
        if (ircOpen) {
            val point = feedbackFor(ircPanel).pointer(mx, py, ircPanel.x, ircPanel.y, ircPanel.width, ircPanel.totalHeight)
            if (ircPanel.mouseScrolled(point.first, point.second, scrollX.toFloat(), scrollY.toFloat())) return true
        }
        if (configPanel.isOpen) {
            val point = feedbackFor(configPanel).pointer(mx, py,
                configPanel.x, configPanel.y, configPanel.width, configPanel.totalHeight)
            if (configPanel.mouseScrolled(point.first, point.second, scrollY.toFloat())) return true
        }
        for (panel in panels.reversed()) {
            val point = feedbackFor(panel).pointer(mx, py, panel.x, panel.y, panel.width, panel.totalHeight)
            if (panel.mouseScrolled(point.first, point.second, scrollX.toFloat(), scrollY.toFloat())) {
                return true
            }
        }
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val keyCode = event.key()
        val scanCode = event.scancode()
        val modifiers = event.modifiers()

        if (NocturneArcadeModal.isOpen) {
            if (NocturneArcadeModal.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }

        if (configPanel.isOpen && configPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }

        if (ircOpen && ircPanel.keyPressed(keyCode, scanCode, modifiers)) {
            return true
        }

        if (isSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                isSearchFocused = false
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                val query = searchText.trim()
                if (query.equals("nocturne", ignoreCase = true) ||
                    query.equals("arcade", ignoreCase = true) ||
                    query.equals("game", ignoreCase = true) ||
                    query.equals("moon", ignoreCase = true)) {
                    NocturneArcadeModal.open()
                    searchText = ""
                    isSearchFocused = false
                    return true
                }
                isSearchFocused = false
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (searchText.isNotEmpty()) {
                    searchText = searchText.substring(0, searchText.length - 1)
                }
                return true
            }
        }

        if (StringSettingComponent.hasFocusedInput() || BedDefenderPreviewComponent.hasFocusedPreview()) {
            for (panel in panels.reversed()) {
                if (panel.keyPressed(keyCode, scanCode, modifiers)) return true
            }
        }

        val toggleKey = ClickGUIModule.keyCode
        val isToggle = (toggleKey != GLFW.GLFW_KEY_UNKNOWN && keyCode == toggleKey) || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || isToggle) {
            if (isToggle && isClosing) {

                isClosing = false
                ClickGUIModule.enabled = true
                val animMultiplier = ClickGUIModule.animSpeed.getFloat()
                openAnim.durationMs = (220L / animMultiplier).toLong().coerceIn(60L, 400L)
                openAnim.animateTo(1f)
                return true
            }
            startClose()
            return true
        }

        for (panel in panels.reversed()) {
            if (panel.keyPressed(keyCode, scanCode, modifiers)) {
                return true
            }
        }

        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (configPanel.charTyped(event.codepoint)) return true
        if (ircOpen && ircPanel.charTyped(event.codepoint)) {
            return true
        }
        if (isSearchFocused && event.codepoint > 0) {
            val char = event.codepoint.toChar()
            if (!char.isISOControl()) {
                searchText += char
                return true
            }
        }
        for (panel in panels.reversed()) {
            if (panel.charTyped(event.codepoint)) return true
        }
        return super.charTyped(event)
    }
}
