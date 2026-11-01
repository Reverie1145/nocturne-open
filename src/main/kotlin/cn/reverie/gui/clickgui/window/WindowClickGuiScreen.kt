package cn.reverie.gui.clickgui.window
import cn.reverie.app.config.ConfigManager
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.ModuleManager
import cn.reverie.nocturne.IrcManager
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.math.Rect2f
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
import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.gui.clickgui.ClickGuiPressFeedback
import cn.reverie.gui.clickgui.ClickGuiSearchFieldLayout
import cn.reverie.gui.clickgui.irc.handleIrcClick
import cn.reverie.gui.clickgui.irc.IrcClipboard
import cn.reverie.gui.clickgui.irc.ircRowY
import cn.reverie.gui.clickgui.irc.renderIrcView
import cn.reverie.gui.clickgui.ModuleButton
import cn.reverie.gui.clickgui.setting.StringSettingComponent
/** A single, movable glass workspace. The existing setting components remain the source of truth. */
class WindowClickGuiScreen : Screen(Component.literal("Nocturne Window ClickGUI")) {
    internal class ModuleMotion(module: Module) {
        val hover = FloatAnimation(0f, 0f, 170L, CubicBezier.EASE_OUT)
        val selected = FloatAnimation(0f, 0f, 230L, CubicBezier.EASE_OUT)
        val enabled = FloatAnimation(if (module.enabled) 1f else 0f, if (module.enabled) 1f else 0f, 260L, CubicBezier.EASE_OUT)
    }

    companion object {
        internal const val W = 790f
        internal const val H = 490f
        internal const val SIDEBAR_W = 148f
        internal const val LIST_X = 164f
        internal const val LIST_W = 254f
        internal const val DETAIL_X = 432f
        internal const val DETAIL_W = 342f
        internal const val CONTENT_TOP = 108f
        internal const val CONTENT_BOTTOM = 470f
        internal const val SIDEBAR_Y = 82f
        internal const val SIDEBAR_ROW_H = 29f
        internal const val SIDEBAR_ROW_STEP = 32f
        internal const val ROW_H = 43f
        internal const val ROW_STEP = 49f

        internal var lastListScroll = 0f
        internal var lastDetailScroll = 0f
        internal var lastSelectedCategory: Category? = null
        internal var lastSelectedModule: Module? = null
        internal var lastSearch = ""
    }

    private data class WindowFrame(val x: Float, val y: Float, val scale: Float)
    internal val buttons = ModuleManager.modules.associateWith { ModuleButton(it) }
    private val pressFeedback = ClickGuiPressFeedback()
    internal var configSelected = false
    internal var selectedConfig: String? = null
    internal var configListScroll = 0f
    internal var configListScrollTarget = 0f
    internal var configNameInput = ""
    internal var configInputFocused = false
    internal var configCursorTimer = 0L
    internal var configStatus = ""
    internal var configStatusAt = 0L
    internal var configStatusIsSuccess = true
    internal var configSharePickerOpen = false
    internal var pendingDeleteConfig: String? = null
    internal var pendingOverwriteConfig: String? = null
    internal val configHoverAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    internal val configSelectAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)

    fun setConfigStatus(msg: String, isSuccess: Boolean = true) {
        configStatus = msg
        configStatusAt = System.currentTimeMillis()
        configStatusIsSuccess = isSuccess
    }

    internal val openAnim = FloatAnimation(0f, 1f, 360L, CubicBezier.EASE_OUT_BACK)
    internal val searchFocusAnim = FloatAnimation(0f, 0f, 190L, CubicBezier.EASE_OUT)
    internal val closeHoverAnim = FloatAnimation(0f, 0f, 130L, CubicBezier.EASE_OUT)
    internal val listTransitionAnim = FloatAnimation(1f, 1f, 280L, CubicBezier.EASE_OUT)
    internal val detailTransitionAnim = FloatAnimation(1f, 1f, 240L, CubicBezier.EASE_OUT)
    internal val rowMotions = ModuleManager.modules.associateWith { ModuleMotion(it) }
    internal val categoryHoverMotions = (listOf<Category?>(null) + Category.entries)
        .associateWith { FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT) }
    internal val categorySelectMotions = (listOf<Category?>(null) + Category.entries)
        .associateWith { FloatAnimation(if (it == null) 1f else 0f, if (it == null) 1f else 0f, 220L, CubicBezier.EASE_OUT) }
    var isClosing = false
        private set
    var isSwitchingStyle = false

    internal var selectedCategory: Category? = null
    internal var selectedModule: Module? = ModuleManager.modules.firstOrNull()
    internal var moduleSharePickerOpen = false
    internal var ircOffersOpen = false
    internal var search: String = Companion.lastSearch
        set(value) {
            field = value
            Companion.lastSearch = value
        }
    internal var searchFocused = false
    internal var searchField = ClickGuiSearchFieldLayout(Rect2f(183f, 16f, 330f, 29f))
    internal var listScroll = 0f
    internal var listScrollTarget = 0f
    internal var detailScroll = 0f
    internal var detailScrollTarget = 0f
    internal var detailContentOffsetX = 0f
    internal var dragOffsetX = 0f
    internal var dragOffsetY = 0f
    internal var dragging = false
    internal var dragStartX = 0f
    internal var dragStartY = 0f
    var logoClickCount = 0
    var lastLogoClickTime = 0L

    internal var ircSelected = false
    internal var ircTarget: String? = null
    internal var ircInput = ""
    internal var ircInputFocused = false
    internal var ircCursorTimer = 0L
    internal var ircScroll = 0f
    internal var ircScrollTarget = 0f
    internal var ircPinned = true
    internal var ircMemberScroll = 0f
    internal var ircMemberScrollTarget = 0f
    internal var lastIrcImageClickAt = 0L
    internal var lastIrcImageClickUrl: String? = null
    internal val ircHoverAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    internal val ircSelectAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
    internal val ircInputFocusAnim = FloatAnimation(0f, 0f, 190L, CubicBezier.EASE_OUT)

    init {
        selectedCategory = Companion.lastSelectedCategory
        val remembered = Companion.lastSelectedModule
        if (remembered != null && ModuleManager.modules.contains(remembered)) {
            selectedModule = remembered
        }
        listScroll = Companion.lastListScroll
        listScrollTarget = Companion.lastListScroll
        detailScroll = Companion.lastDetailScroll
        detailScrollTarget = Companion.lastDetailScroll
        categorySelectMotions.forEach { (cat, anim) -> anim.setImmediate(if (cat == selectedCategory) 1f else 0f) }
    }

    override fun init() {
        super.init()
        pressFeedback.reset()
        if (!isClosing) {
            openAnim.setImmediate(0f)
            openAnim.animateTo(1f)
            listTransitionAnim.setImmediate(0f)
            listTransitionAnim.animateTo(1f)
            detailTransitionAnim.setImmediate(0f)
            detailTransitionAnim.animateTo(1f)
            categorySelectMotions.values.forEach { it.setImmediate(0f) }
        }
    }

    fun reopen() {
        isClosing = false
        ClickGUIModule.enabled = true
        openAnim.durationMs = (220L / ClickGUIModule.animSpeed.getFloat()).toLong().coerceIn(60L, 400L)
        openAnim.animateTo(1f)
    }

    fun startClose() {
        if (isClosing) return
        isClosing = true
        pressFeedback.releaseAll()
        BedDefenderPreviewComponent.clearFocus()
        StringSettingComponent.clearFocus()
        searchFocused = false
        dragging = false
        NocturneArcadeModal.close()
        persistUiState()
        openAnim.durationMs = (180L / ClickGUIModule.animSpeed.getFloat()).toLong().coerceIn(60L, 400L)
        openAnim.animateTo(0f)
        if (ClickGUIModule.enabled) ClickGUIModule.enabled = false
        ConfigManager.flush()
    }

    override fun onClose() = startClose()
    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        super.removed()
        BedDefenderPreviewComponent.clearFocus()
        StringSettingComponent.clearFocus()
        NocturneArcadeModal.close()
        persistUiState()
        if (isSwitchingStyle) return
        if (ClickGUIModule.enabled) ClickGUIModule.enabled = false
        ConfigManager.flush()
    }

    private fun persistUiState() {
        Companion.lastListScroll = listScrollTarget
        Companion.lastDetailScroll = detailScrollTarget
        Companion.lastSelectedCategory = selectedCategory
        Companion.lastSelectedModule = selectedModule
        Companion.lastSearch = search
    }

    internal fun sendIrcMessage() {
        val text = ircInput.trim()
        if (text.isEmpty()) return
        val privateCommand = Regex("^/msg\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(text)
        val target = privateCommand?.groupValues?.get(1) ?: (ircTarget ?: IrcManager.channel)
        val body = privateCommand?.groupValues?.get(2) ?: text
        if (IrcManager.send(body, target)) {
            if (privateCommand != null) ircTarget = target
            ircInput = ""
            ircPinned = true
        }
    }

    private fun frame(): WindowFrame {        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val baseScale = minOf(1f, (screenW - 20f) / W, (screenH - 20f) / H).coerceAtLeast(0.25f)
        val scale = baseScale * (0.94f + openAnim.value * 0.06f)
        val actualW = W * scale
        val actualH = H * scale
        val x = ((screenW - actualW) * 0.5f + dragOffsetX).coerceIn(0f, (screenW - actualW).coerceAtLeast(0f))
        val y = ((screenH - actualH) * 0.5f + dragOffsetY).coerceIn(0f, (screenH - actualH).coerceAtLeast(0f))
        return WindowFrame(x, y, scale)
    }

    private fun localPointer(x: Float, y: Float, frame: WindowFrame): Pair<Float, Float> =
        pressFeedback.pointer((x - frame.x) / frame.scale, (y - frame.y) / frame.scale, 0f, 0f, W, H)
    internal fun inside(x: Float, y: Float, rx: Float, ry: Float, rw: Float, rh: Float) =
        x >= rx && x <= rx + rw && y >= ry && y <= ry + rh

    private fun visibleModules(): List<Module> {
        val query = search.trim()
        return ModuleManager.modules.filter { module ->
            (query.isNotEmpty() || selectedCategory == null || module.category == selectedCategory) &&
                (query.isEmpty() || module.name.contains(query, true) || module.description.contains(query, true) ||
                    module.category.displayName.contains(query, true))
        }
    }

    private fun restartListTransition() {
        listTransitionAnim.setImmediate(0f)
        listTransitionAnim.durationMs = 280L
        listTransitionAnim.animateTo(1f)
    }

    private fun selectModule(module: Module) {
        if (selectedModule == module) return
        selectedModule = module
        detailScroll = 0f
        detailScrollTarget = 0f
        detailTransitionAnim.setImmediate(0f)
        detailTransitionAnim.animateTo(1f)
    }

    internal fun rowEntryProgress(index: Int): Float {
        val staggered = ((listTransitionAnim.value * 1.45f) - index * 0.028f).coerceIn(0f, 1f)
        return 1f - (1f - staggered) * (1f - staggered) * (1f - staggered)
    }

    internal fun rowY(index: Int): Float = CONTENT_TOP + index * ROW_STEP - listScroll + (1f - rowEntryProgress(index)) * 13f

    private fun reconcileSelection(modules: List<Module>) {
        if (selectedModule !in modules) {
            val first = modules.firstOrNull()
            if (first == null) {
                selectedModule = null
                detailScroll = 0f
                detailScrollTarget = 0f
            } else selectModule(first)
        }
    }

    fun renderClickGui(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (isClosing && (openAnim.isFinished || openAnim.value <= 0.02f)) {
            Minecraft.getInstance().gui.setScreen(null)
            return
        }
        val alpha = openAnim.value.coerceIn(0f, 1f)
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val frame = frame()
        val highlight = NanoVGColor(0.91f, 0.92f, 0.94f, 1f)

        NanoVGRenderer.drawRoundedRect(0f, 0f, screenW, screenH, 0f,
            NanoVGColor(0f, 0f, 0f, 0.35f * alpha))
        val transform = RenderEngine.nanoVG.transform
        transform.save()
        try {
            transform.translate(frame.x, frame.y)
            transform.scale(frame.scale, frame.scale)
            pressFeedback.withSurface(0f, 0f, W, H, 13f, alpha) {
                val (mx, my) = localPointer(mouseX, mouseY, frame)
                val compression = pressFeedback.compression
                if (HUDModule.widgetShadow.value) Draw2D.shadow(0f, 4f - 2.5f * compression, W, H,
                    14f, 24f - 14f * compression,
                    NanoVGColor(0f, 0f, 0f, 0.47f * alpha * HUDModule.shadowStrength.getFloat()))
                try {
                    RenderEngine.backdropBlurRenderer.renderBackdrop(0f, 0f, W, H,
                        radius = 13f,
                        tintColor = NanoVGColor(0.047f, 0.051f, 0.067f, HUDModule.bgOpacity.getFloat() * alpha),
                        blurRadius = HUDModule.blurRadius.getFloat(),
                        enableBlur = HUDModule.blur.value,
                        backdropOpacity = HUDModule.bgOpacity.getFloat() * alpha)
                } catch (_: Throwable) {
                    NanoVGRenderer.drawRoundedRect(0f, 0f, W, H, 13f,
                        NanoVGColor(0.047f, 0.051f, 0.067f, HUDModule.bgOpacity.getFloat() * alpha))
                }
                NanoVGRenderer.drawRoundedOutline(0f, 0f, W, H, 13f, NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 1f)
                NanoVGRenderer.drawRoundedRect(1f, 62f, SIDEBAR_W - 1f, H - 63f, 0f,
                    NanoVGColor(0f, 0f, 0f, 0.12f * alpha))
                Draw2D.line(0f, 61f, W, 61f, NanoVGColor(1f, 1f, 1f, 0.08f * alpha), 1f)

                renderHeader(mx, my, highlight, alpha)
                renderSidebar(mx, my, highlight, alpha)
                if (configSelected) {
                    renderConfigView(mx, my, partialTicks, highlight, alpha)
                } else if (ircSelected) {
                    renderIrcView(mx, my, alpha)
                } else {
                    val modules = visibleModules()
                    reconcileSelection(modules)
                    renderList(mx, my, modules, highlight, alpha)
                    renderDetails(mx, my, partialTicks, highlight, alpha)
                }

            }
            persistUiState()
        } finally {
            transform.restore()
        }
        if (NocturneArcadeModal.isOpen) {
            val canvasX = ResolutionHelper.toCanvasX(mouseX)
            val canvasY = ResolutionHelper.toCanvasY(mouseY)
            NocturneArcadeModal.render(canvasX, canvasY, partialTicks)
        }
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {}
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        StringSettingComponent.clearFocus()
        BedDefenderPreviewComponent.clearFocus()
        val canvasX = ResolutionHelper.toCanvasX(event.x().toFloat())
        val canvasY = ResolutionHelper.toCanvasY(event.y().toFloat())
        val button = event.button()
        if (NocturneArcadeModal.isOpen) {
            if (NocturneArcadeModal.mouseClicked(canvasX, canvasY, button)) return true
        }
        val frame = frame()
        val (mx, my) = localPointer(canvasX, canvasY, frame)
        if (!inside(mx, my, 0f, 0f, W, H)) {
            searchFocused = false
            return true
        }
        pressFeedback.press(mx, my, 0f, 0f, W, H, button)
        if (searchField.bounds.contains(mx, my)) {
            if (button == 0) {
                if (search.isNotEmpty() && searchField.clearButton.contains(mx, my)) {
                    search = ""
                    listScrollTarget = 0f
                    restartListTransition()
                }
                searchFocused = true
                ircInputFocused = false
                configInputFocused = false
            }
            return true
        }
        if (button == 0) {
            if (inside(mx, my, 755f, 17f, 22f, 27f)) { startClose(); return true }
            searchFocused = false
            if (inside(mx, my, 0f, 0f, W, 61f)) {
                if (inside(mx, my, 10f, 10f, 160f, 45f)) {
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
                        return true
                    } else {
                        ArcadeSoundHelper.playComboTick(logoClickCount)
                    }
                }
                dragging = true
                dragStartX = ResolutionHelper.toCanvasX(event.x().toFloat()) - frame.x
                dragStartY = ResolutionHelper.toCanvasY(event.y().toFloat()) - frame.y
                return true
            }
            if (inside(mx, my, 10f, SIDEBAR_Y, 129f, SIDEBAR_ROW_H)) {
                selectedCategory = null
                ircSelected = false
                configSelected = false
                listScrollTarget = 0f
                restartListTransition()
                return true
            }
            for ((index, category) in Category.entries.withIndex()) {
                if (inside(mx, my, 10f, SIDEBAR_Y + (index + 1) * SIDEBAR_ROW_STEP, 129f, SIDEBAR_ROW_H)) {
                    selectedCategory = category
                    ircSelected = false
                    configSelected = false
                    listScrollTarget = 0f
                    restartListTransition()
                    return true
                }
            }
            if (inside(mx, my, 10f, configRowY(), 129f, SIDEBAR_ROW_H)) {
                configSelected = true
                ircSelected = false
                selectedCategory = null
                configInputFocused = false
                return true
            }
            if (inside(mx, my, 10f, ircRowY(), 129f, SIDEBAR_ROW_H)) {
                ircSelected = true
                configSelected = false
                selectedCategory = null
                ircPinned = true
                ircInputFocused = false
                return true
            }
        }
        if (configSelected) {
            if (handleConfigClick(mx, my, button, doubleClick)) return true
            return true
        }
        if (ircSelected) {
            handleIrcClick(mx, my, button, doubleClick)
            return true
        }
        if (inside(mx, my, LIST_X, CONTENT_TOP, LIST_W, CONTENT_BOTTOM - CONTENT_TOP)) {
            val modules = visibleModules()
            for ((index, module) in modules.withIndex()) {
                val y = rowY(index)
                if (!inside(mx, my, LIST_X, y, LIST_W, ROW_H)) continue
                val motion = rowMotions.getValue(module)
                val entry = rowEntryProgress(index)
                val visualX = LIST_X + (1f - entry) * 8f - motion.hover.value * 1.5f
                val visualW = LIST_W - (1f - entry) * 8f
                if (button == 0 && mx >= visualX + visualW - 49f) module.toggle()
                else if (button == 0) selectModule(module)
                else if (button == 1) { selectModule(module); module.toggle() }
                return true
            }
            return true
        }
        val module = selectedModule
        if (module != null && inside(mx, my, DETAIL_X, 77f, DETAIL_W, 393f)) {
            val detailMouseX = mx - detailContentOffsetX
            val shareX = DETAIL_X + DETAIL_W - 137f
            if (button == 0 && inside(detailMouseX, my, shareX, 155f, 126f, 19f)) {
                moduleSharePickerOpen = !moduleSharePickerOpen
                return true
            }
            if (moduleSharePickerOpen) {
                val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(6)
                if (button == 0 && inside(detailMouseX, my, DETAIL_X + 10f, 179f, DETAIL_W - 20f, 279f)) {
                    if (my >= 205f) {
                        val offset = my - 205f
                        val index = (offset / 30f).toInt()
                        val inRow = offset % 30f <= 26f
                        if (!inRow) return true
                        contacts.getOrNull(index)?.let { cn.reverie.nocturne.IrcShareManager.sendModuleConfig(it, module.name) }
                        if (contacts.getOrNull(index) != null) moduleSharePickerOpen = false
                    }
                    return true
                }
                moduleSharePickerOpen = false
            }
            if (button == 0 && inside(detailMouseX, my, DETAIL_X + DETAIL_W - 52f, 84f, 44f, 27f)) {
                module.toggle(); return true
            }
            if (inside(detailMouseX, my, DETAIL_X + 10f, 179f, DETAIL_W - 20f, 279f)) {
                for (comp in buttons[module]?.settingComponents.orEmpty()) {
                    if (comp.isVisible && comp.isMouseOver(detailMouseX, my) && comp.mouseClicked(detailMouseX, my, button)) return true
                }
            }
            return true
        }
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val canvasX = ResolutionHelper.toCanvasX(event.x().toFloat())
        val canvasY = ResolutionHelper.toCanvasY(event.y().toFloat())
        if (NocturneArcadeModal.isOpen && NocturneArcadeModal.mouseReleased(canvasX, canvasY, event.button())) return true
        pressFeedback.release(event.button())
        if (event.button() == 0) dragging = false
        val frame = frame()
        val point = localPointer(canvasX, canvasY, frame)
        val mx = point.first - detailContentOffsetX
        val my = point.second
        var handled = false
        for (comp in selectedModule?.let { buttons[it]?.settingComponents }.orEmpty()) {
            handled = comp.mouseReleased(mx, my, event.button()) || handled
        }
        return handled || super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mouseX = ResolutionHelper.toCanvasX(event.x().toFloat())
        val mouseY = ResolutionHelper.toCanvasY(event.y().toFloat())
        val dragX = ResolutionHelper.toCanvasDelta(dx)
        val dragY = ResolutionHelper.toCanvasDelta(dy)
        if (NocturneArcadeModal.isOpen && NocturneArcadeModal.mouseDragged(mouseX, mouseY, event.button(), dragX.toFloat(), dragY.toFloat())) return true
        if (dragging && event.button() == 0) {
            pressFeedback.release(event.button())
            val screenW = ResolutionHelper.scaledWidth
            val screenH = ResolutionHelper.scaledHeight
            val scale = frame().scale
            val x = (mouseX - dragStartX).coerceIn(0f, (screenW - W * scale).coerceAtLeast(0f))
            val y = (mouseY - dragStartY).coerceIn(0f, (screenH - H * scale).coerceAtLeast(0f))
            dragOffsetX = x - (screenW - W * scale) * 0.5f
            dragOffsetY = y - (screenH - H * scale) * 0.5f
            return true
        }
        val frame = frame()
        val point = localPointer(mouseX, mouseY, frame)
        val mx = point.first - detailContentOffsetX
        val my = point.second
        val delta = pressFeedback.delta((dragX / frame.scale).toFloat(), (dragY / frame.scale).toFloat(), 0f, 0f, W, H)
        for (comp in selectedModule?.let { buttons[it]?.settingComponents }.orEmpty()) {
            if (comp.mouseDragged(mx, my, event.button(), delta.first, delta.second)) return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val frame = frame()
        val (mx, my) = localPointer(ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat(), frame)
        if (configSelected) {
            configListScrollTarget = (configListScrollTarget - scrollY.toFloat() * 29f).coerceAtLeast(0f)
            return true
        }
        if (ircSelected) {
            if (inside(mx, my, LIST_X, CONTENT_TOP, LIST_W, CONTENT_BOTTOM - CONTENT_TOP)) {
                ircMemberScrollTarget = (ircMemberScrollTarget - scrollY.toFloat() * 29f).coerceAtLeast(0f)
                return true
            }
            if (inside(mx, my, DETAIL_X, 77f, DETAIL_W, 393f)) {
                ircScrollTarget = (ircScrollTarget - scrollY.toFloat() * 24f).coerceAtLeast(0f)
                return true
            }
            return true
        }
        if (inside(mx, my, LIST_X, CONTENT_TOP, LIST_W, CONTENT_BOTTOM - CONTENT_TOP)) {
            listScrollTarget = (listScrollTarget - scrollY.toFloat() * 29f).coerceAtLeast(0f)
            return true
        }
        if (inside(mx, my, DETAIL_X, 179f, DETAIL_W, 279f)) {
            detailScrollTarget = (detailScrollTarget - scrollY.toFloat() * 29f).coerceAtLeast(0f)
            return true
        }
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()
        if (NocturneArcadeModal.isOpen) {
            if (NocturneArcadeModal.keyPressed(key, event.scancode(), event.modifiers())) return true
        }
        if (searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false
                return true
            }
            if (key == GLFW.GLFW_KEY_ENTER) {
                val query = search.trim()
                if (query.equals("nocturne", ignoreCase = true) ||
                    query.equals("arcade", ignoreCase = true) ||
                    query.equals("game", ignoreCase = true) ||
                    query.equals("moon", ignoreCase = true)) {
                    NocturneArcadeModal.open()
                    search = ""
                    searchFocused = false
                    return true
                }
                searchFocused = false
                return true
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (search.isNotEmpty()) {
                    search = search.dropLast(1)
                    listScrollTarget = 0f
                    restartListTransition()
                }
                return true
            }
        }
        if (configSelected && configInputFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> { configInputFocused = false; return true }
                GLFW.GLFW_KEY_ENTER -> {
                    val targetName = configNameInput.trim()
                    if (targetName.isNotEmpty() && cn.reverie.app.config.ConfigManager.isValidConfigName(targetName)) {
                        val ok = cn.reverie.app.config.ConfigManager.saveConfig(targetName)
                        if (ok) {
                            selectedConfig = targetName
                            configNameInput = ""
                            configInputFocused = false
                            setConfigStatus("Created profile: $targetName", isSuccess = true)
                        } else {
                            setConfigStatus("Failed to create profile", isSuccess = false)
                        }
                    }
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (configNameInput.isNotEmpty()) configNameInput = configNameInput.dropLast(1)
                    configCursorTimer = System.currentTimeMillis()
                    return true
                }
            }
        }
        if (configSelected && key == GLFW.GLFW_KEY_ENTER) return true
        if (ircSelected && ircInputFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> { ircInputFocused = false; return true }
                GLFW.GLFW_KEY_ENTER -> { sendIrcMessage(); return true }
                GLFW.GLFW_KEY_V -> if (event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
                    val pasted = IrcClipboard.paste(ircTarget ?: IrcManager.channel, ircInput, 240)
                    pasted.text?.let { ircInput = it }
                    ircCursorTimer = System.currentTimeMillis()
                    return pasted.imageHandled || pasted.text != null
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (ircInput.isNotEmpty()) ircInput = ircInput.dropLast(1)
                    ircCursorTimer = System.currentTimeMillis()
                    return true
                }
            }
        }
        if (ircSelected && key == GLFW.GLFW_KEY_ENTER) return true
        if (searchFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> { searchFocused = false; return true }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (search.isNotEmpty()) search = search.dropLast(1)
                    listScrollTarget = 0f
                    restartListTransition()
                    return true
                }
                GLFW.GLFW_KEY_A -> if (event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
                    search = ""; listScrollTarget = 0f; restartListTransition(); return true
                }
            }
        }
        for (comp in selectedModule?.let { buttons[it]?.settingComponents }.orEmpty()) {
            if (comp.isVisible && comp.keyPressed(key, event.scancode(), event.modifiers())) return true
        }
        if (key == GLFW.GLFW_KEY_F && event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0 && !ircSelected && !configSelected) {
            searchFocused = true
            return true
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            if (ircSelected || configSelected) return true
            val modules = visibleModules()
            if (modules.isNotEmpty()) {
                val currentIndex = modules.indexOf(selectedModule)
                val nextIndex = (currentIndex + if (key == GLFW.GLFW_KEY_DOWN) 1 else -1)
                    .coerceIn(0, modules.lastIndex)
                selectModule(modules[nextIndex])
                val rowY = nextIndex * ROW_STEP
                if (rowY < listScrollTarget) listScrollTarget = rowY
                if (rowY + ROW_H > listScrollTarget + CONTENT_BOTTOM - CONTENT_TOP) {
                    listScrollTarget = rowY + ROW_H - (CONTENT_BOTTOM - CONTENT_TOP)
                }
            }
            return true
        }
        if (key == GLFW.GLFW_KEY_ENTER) {
            if (!ircSelected && !configSelected) selectedModule?.toggle()
            return true
        }
        val toggleKey = ClickGUIModule.keyCode
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT ||
            (toggleKey != GLFW.GLFW_KEY_UNKNOWN && key == toggleKey)) {
            startClose()
            return true
        }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (configSelected && configInputFocused && event.codepoint > 0) {
            val c = event.codepoint.toChar()
            if (!c.isISOControl() && configNameInput.length < 32) {
                configNameInput += c
                configCursorTimer = System.currentTimeMillis()
                return true
            }
        }
        if (ircSelected && ircInputFocused && event.codepoint > 0) {
            val c = event.codepoint.toChar()
            if (!c.isISOControl() && ircInput.length < 240) {
                ircInput += c
                ircCursorTimer = System.currentTimeMillis()
                return true
            }
        }
        if (searchFocused && event.codepoint > 0) {
            val c = event.codepoint.toChar()
            if (!c.isISOControl() && search.length < 80) {
                search += c
                listScrollTarget = 0f
                restartListTransition()
                return true
            }
        }
        for (comp in selectedModule?.let { buttons[it]?.settingComponents }.orEmpty()) {
            if (comp.isVisible && comp.charTyped(event.codepoint.toChar(), 0)) return true
        }
        return super.charTyped(event)
    }
}
