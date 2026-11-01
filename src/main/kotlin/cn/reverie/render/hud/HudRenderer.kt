package cn.reverie.render.hud

import cn.reverie.app.config.ConfigManager
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.render.ResolutionChangeEvent
import cn.reverie.module.ModuleManager
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.impl.*
import cn.reverie.render.hud.impl.arraylist.ArrayListWidget
import cn.reverie.render.hud.impl.keybind.KeybindHUDWidget
import cn.reverie.render.nanovg.NanoVGTransform
import net.minecraft.client.Minecraft
import net.minecraft.client.DeltaTracker
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList
import cn.reverie.render.ui.notification.NotificationManager
class HudRenderer {

    val elements = CopyOnWriteArrayList<HudElement>()

    var draggingElement: HudElement? = null
    val activeSnapLines = mutableListOf<SnapLine>()

    var gridSnapEnabled: Boolean = false
    var gridSize: Float = 10f

    var selectedElement: HudElement? = null
        set(value) {
            field?.isSelected = false
            field = value
            value?.isSelected = true
        }

    private var wasLeftDown = false
    private var wasRightDown = false
    private var isInitialized = false
    private var canvasWidth = ResolutionHelper.scaledWidth
    private var canvasHeight = ResolutionHelper.scaledHeight

    init {
        registerDefaultElements()
        initConfig()
        canvasWidth = ResolutionHelper.scaledWidth
        canvasHeight = ResolutionHelper.scaledHeight
        EventBus.register<ResolutionChangeEvent> { onResolutionChanged() }
    }

    private fun registerDefaultElements() {
        registerElement(WatermarkWidget())
        registerElement(NotificationWidget())
        registerElement(ArrayListWidget())
        registerElement(ScoreboardWidget)
        registerElement(TargetHUDWidget())
        registerElement(Keystrokes())
        registerElement(ArmorHUDWidget())
        registerElement(PotionHUDWidget())
        registerElement(KeybindHUDWidget())
        registerElement(DynamicIslandWidget())
        registerElement(MusicHUDWidget())
        registerElement(LyricHUDWidget())
    }

    fun registerElement(element: HudElement) {
        if (!elements.contains(element)) {
            elements.add(element)
            ModuleManager.register(element)
        }
    }

    fun shouldInterceptVanillaPotion(): Boolean {
        val potionHud = elements.filterIsInstance<PotionHUDWidget>().firstOrNull() ?: return false
        return potionHud.enabled && potionHud.interceptVanillaPotion.value
    }

    fun unregisterElement(element: HudElement) {
        elements.remove(element)
    }

    fun initConfig() {
        if (isInitialized) return
        try {
            ResolutionHelper.syncWithMinecraft()
        } catch (_: Throwable) {}
        HudConfigManager.load(elements)
        canvasWidth = ResolutionHelper.scaledWidth
        canvasHeight = ResolutionHelper.scaledHeight
        isInitialized = true
    }

    private fun onResolutionChanged() {
        val nextWidth = ResolutionHelper.scaledWidth
        val nextHeight = ResolutionHelper.scaledHeight
        // Ignore transient invalid sizes (minimized window / mid-resize); keep last good canvas.
        if (!nextWidth.isFinite() || !nextHeight.isFinite() || nextWidth < 64f || nextHeight < 64f) return
        val widthDelta = if (canvasWidth > 0f && nextWidth > 0f) nextWidth - canvasWidth else 0f
        val heightDelta = if (canvasHeight > 0f && nextHeight > 0f) nextHeight - canvasHeight else 0f
        if (widthDelta != 0f) {
            elements.forEach { it.x += widthDelta * it.horizontalAnchor.resizeFactor }
        }
        if (heightDelta != 0f) {
            elements.forEach { it.y += heightDelta * it.verticalAnchor.resizeFactor }
        }
        if (widthDelta != 0f || heightDelta != 0f) {
            elements.forEach { it.onCanvasResized(widthDelta, heightDelta) }
        }
        canvasWidth = nextWidth
        canvasHeight = nextHeight

        draggingElement?.isDragging = false
        draggingElement = null
        activeSnapLines.clear()
        wasLeftDown = false
        wasRightDown = false
        clampElementsToViewport()
    }

    private fun clampElementsToViewport() {
        elements.forEach(::clampElementToViewport)
    }

    private fun clampElementToViewport(element: HudElement) {
        val maxX = (ResolutionHelper.scaledWidth - element.scaledWidth).coerceAtLeast(0f)
        val maxY = (ResolutionHelper.scaledHeight - element.scaledHeight).coerceAtLeast(0f)
        element.x = if (element.x.isFinite()) element.x.coerceIn(0f, maxX) else 0f
        element.y = if (element.y.isFinite()) element.y.coerceIn(0f, maxY) else 0f
    }

    fun onTick() {
        if (!isInitialized) initConfig()
        HUDModule.crosshair.onTick()
        elements.forEach { it.onTick() }
    }

    fun resetAllPositions() {
        for (element in elements) {
            element.horizontalAnchor = element.defaultHorizontalAnchor
            element.verticalAnchor = element.defaultVerticalAnchor
            element.resetPosition()
            element.widgetScale.value = 1.0
        }
        HudConfigManager.save(elements)
        ConfigManager.markDirty()
    }

    fun resetElement(element: HudElement) {
        element.horizontalAnchor = element.defaultHorizontalAnchor
        element.verticalAnchor = element.defaultVerticalAnchor
        element.resetPosition()
        element.widgetScale.value = 1.0
        HudConfigManager.save(elements)
        ConfigManager.markDirty()
    }

    fun nudgeElement(element: HudElement, dx: Float, dy: Float) {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val elemW = element.scaledWidth
        val elemH = element.scaledHeight

        element.x = (element.x + dx).coerceIn(0f, (screenW - elemW).coerceAtLeast(0f))
        element.y = (element.y + dy).coerceIn(0f, (screenH - elemH).coerceAtLeast(0f))
        updatePositionAnchors(element)

        HudConfigManager.save(elements)
        ConfigManager.markDirty()
    }

    fun centerElement(element: HudElement) {
        alignElement(element, 0, 0)
    }

    fun alignElement(element: HudElement, hAlign: Int, vAlign: Int) {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val elemW = element.scaledWidth
        val elemH = element.scaledHeight
        val pad = 8f

        val targetX = when (hAlign) {
            -1 -> pad
            0 -> (screenW - elemW) * 0.5f
            1 -> screenW - pad - elemW
            else -> element.x
        }

        val targetY = when (vAlign) {
            -1 -> pad
            0 -> (screenH - elemH) * 0.5f
            1 -> screenH - pad - elemH
            else -> element.y
        }

        element.x = targetX.coerceIn(0f, (screenW - elemW).coerceAtLeast(0f))
        element.y = targetY.coerceIn(0f, (screenH - elemH).coerceAtLeast(0f))

        if (hAlign in -1..1) {
            element.horizontalAnchor = when (hAlign) {
                -1 -> HudHorizontalAnchor.LEFT
                0 -> HudHorizontalAnchor.CENTER
                else -> HudHorizontalAnchor.RIGHT
            }
        }
        if (vAlign in -1..1) {
            element.verticalAnchor = when (vAlign) {
                -1 -> HudVerticalAnchor.TOP
                0 -> HudVerticalAnchor.CENTER
                else -> HudVerticalAnchor.BOTTOM
            }
        }

        HudConfigManager.save(elements)
        ConfigManager.markDirty()
    }

    private fun updatePositionAnchors(element: HudElement) {
        val availableX = (ResolutionHelper.scaledWidth - element.scaledWidth).coerceAtLeast(0f)
        val availableY = (ResolutionHelper.scaledHeight - element.scaledHeight).coerceAtLeast(0f)
        element.horizontalAnchor = listOf(
            HudHorizontalAnchor.LEFT to kotlin.math.abs(element.x),
            HudHorizontalAnchor.CENTER to kotlin.math.abs(element.x - availableX * 0.5f),
            HudHorizontalAnchor.RIGHT to kotlin.math.abs(availableX - element.x)
        ).minBy { it.second }.first
        element.verticalAnchor = listOf(
            HudVerticalAnchor.TOP to kotlin.math.abs(element.y),
            HudVerticalAnchor.CENTER to kotlin.math.abs(element.y - availableY * 0.5f),
            HudVerticalAnchor.BOTTOM to kotlin.math.abs(availableY - element.y)
        ).minBy { it.second }.first
    }

    // 自动对齐（磁吸辅助线）
    fun applyPositionWithSnapping(
        element: HudElement,
        rawX: Float,
        rawY: Float,
        snapToEdges: Boolean = HUDModule.snapping.value,
        snapDist: Float = HUDModule.snapDistance.value.toFloat()
    ) {
        activeSnapLines.clear()
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val elemW = element.scaledWidth
        val elemH = element.scaledHeight
        val gridX = if (gridSnapEnabled && gridSize > 1f) kotlin.math.round(rawX / gridSize) * gridSize else rawX
        val gridY = if (gridSnapEnabled && gridSize > 1f) kotlin.math.round(rawY / gridSize) * gridSize else rawY
        val result = HudSnapEngine.resolve(
            moving = HudSnapEngine.Bounds(element.x, element.y, elemW, elemH),
            desiredX = gridX,
            desiredY = gridY,
            screenWidth = screenW,
            screenHeight = screenH,
            others = elements.asSequence()
                .filter { it != element && it.enabled }
                .map { HudSnapEngine.Bounds(it.x, it.y, it.scaledWidth, it.scaledHeight) }
                .toList(),
            snapDistance = snapDist,
            enabled = snapToEdges
        )
        element.x = result.x
        element.y = result.y
        activeSnapLines.addAll(result.lines)
    }

    // 上下左右按键移动选中的控件
    fun onKeyPressed(keyCode: Int, modifiers: Int): Boolean {
        val target = selectedElement ?: elements.firstOrNull { it.isHovered } ?: return false
        val isShift = (modifiers and GLFW.GLFW_MOD_SHIFT) != 0 || isShiftHeld()
        val step = if (isShift) 5f else 1f

        return when (keyCode) {
            GLFW.GLFW_KEY_UP -> {
                nudgeElement(target, 0f, -step)
                true
            }
            GLFW.GLFW_KEY_DOWN -> {
                nudgeElement(target, 0f, step)
                true
            }
            GLFW.GLFW_KEY_LEFT -> {
                nudgeElement(target, -step, 0f)
                true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                nudgeElement(target, step, 0f)
                true
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                if (selectedElement != null) {
                    selectedElement = null
                    true
                } else false
            }
            else -> false
        }
    }

    fun handleFrameInput(mouseX: Float, mouseY: Float, isLeftDown: Boolean, isRightDown: Boolean) {
        if (!isInitialized) initConfig()
        clampElementsToViewport()

        for (element in elements) {
            element.isHovered = element.enabled && element.contains(mouseX, mouseY)
        }

        // 鼠标左键按下：选中并开始拖动控件
        if (isLeftDown && !wasLeftDown) {
            var clickedOnElement = false
            for (element in elements.reversed()) {
                if (element.enabled && element.contains(mouseX, mouseY)) {
                    draggingElement = element
                    selectedElement = element
                    element.isDragging = true
                    element.dragOffsetX = mouseX - element.x
                    element.dragOffsetY = mouseY - element.y
                    clickedOnElement = true
                    break
                }
            }
            if (!clickedOnElement) {
                selectedElement = null
            }
        }

        // 鼠标左键拖拽：自动对齐移动
        if (isLeftDown && draggingElement != null) {
            val element = draggingElement!!
            applyPositionWithSnapping(element, mouseX - element.dragOffsetX, mouseY - element.dragOffsetY)
        }

        // 鼠标左键松开：停止拖动并保存配置
        if (!isLeftDown && wasLeftDown) {
            if (draggingElement != null) {
                val movedElement = draggingElement!!
                updatePositionAnchors(movedElement)
                movedElement.isDragging = false
                draggingElement = null
                activeSnapLines.clear()
                HudConfigManager.save(elements)
                ConfigManager.markDirty()
            }
        }

        // 右键重置位置，Shift+右键切换全局阴影
        if (isRightDown && !wasRightDown) {
            for (element in elements.reversed()) {
                if (element.enabled && element.contains(mouseX, mouseY)) {
                    selectedElement = element
                    if (isShiftHeld()) {
                        HUDModule.widgetShadow.value = !HUDModule.widgetShadow.value
                        ConfigManager.markDirty()
                    } else {
                        resetElement(element)
                    }
                    break
                }
            }
        }

        wasLeftDown = isLeftDown
        wasRightDown = isRightDown
    }

    private fun isShiftHeld(): Boolean {
        val window = Minecraft.getInstance().window.handle()
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
            GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS
    }

    fun clearDrag() {
        if (draggingElement != null) {
            val movedElement = draggingElement!!
            updatePositionAnchors(movedElement)
            movedElement.isDragging = false
            draggingElement = null
            activeSnapLines.clear()
            HudConfigManager.save(elements)
            ConfigManager.markDirty()
        }
        for (element in elements) {
            element.isHovered = false
        }
        wasLeftDown = false
        wasRightDown = false
    }

    fun onMouseClick(mouseX: Float, mouseY: Float, button: Int): Boolean {
        clampElementsToViewport()
        for (element in elements.reversed()) {
            if (element.enabled && element.contains(mouseX, mouseY)) {
                selectedElement = element
                if (button == 0) {
                    draggingElement = element
                    element.isDragging = true
                    element.dragOffsetX = mouseX - element.x
                    element.dragOffsetY = mouseY - element.y
                    return true
                } else if (button == 1) {
                    if (isShiftHeld()) {
                        HUDModule.widgetShadow.value = !HUDModule.widgetShadow.value
                        ConfigManager.markDirty()
                    } else {
                        resetElement(element)
                    }
                    return true
                }
            }
        }
        return false
    }

    fun onMouseRelease(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (draggingElement != null) {
            val movedElement = draggingElement!!
            updatePositionAnchors(movedElement)
            movedElement.isDragging = false
            draggingElement = null
            activeSnapLines.clear()
            HudConfigManager.save(elements)
            ConfigManager.markDirty()
            return true
        }
        return false
    }

    fun onMouseMove(mouseX: Float, mouseY: Float) {
        clampElementsToViewport()
        for (element in elements) {
            element.isHovered = element.enabled && element.contains(mouseX, mouseY)
        }
    }

    fun onMouseDrag(mouseX: Float, mouseY: Float) {
        val element = draggingElement ?: return
        applyPositionWithSnapping(element, mouseX - element.dragOffsetX, mouseY - element.dragOffsetY)
    }

    fun onMouseScrolled(mouseX: Float, mouseY: Float, scrollY: Float): Boolean {
        for (element in elements.reversed()) {
            if (element.enabled && element.contains(mouseX, mouseY)) {
                val step = element.widgetScale.step
                val delta = if (scrollY > 0) step else -step
                val newVal = (element.widgetScale.value + delta).coerceIn(element.widgetScale.min, element.widgetScale.max)
                element.widgetScale.value = (Math.round(newVal / step) * step)
                HudConfigManager.save(elements)
                ConfigManager.markDirty()
                return true
            }
        }
        return false
    }

    fun extractRenderState(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
        if (!isInitialized) initConfig()
        val partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false)
        val mc = Minecraft.getInstance()
        val currentScreen = mc.gui.screen()
        val isChat = currentScreen is ChatScreen
        val isEditing = isChat

        val pose = graphics.pose()
        pose.pushMatrix()
        pose.scale(ResolutionHelper.uiScaleFactor, ResolutionHelper.uiScaleFactor)
        try {
            HUDModule.withHudFont {
                for (element in elements) {
                    if (element.enabled) {
                        pose.pushMatrix()
                        try {
                            val scale = element.scale
                            if (scale != 1f) {
                                pose.translate(element.x, element.y)
                                pose.scale(scale, scale)
                                pose.translate(-element.x, -element.y)
                            }
                            element.extractRenderState(graphics, partialTicks, isEditing)
                        } finally {
                            pose.popMatrix()
                        }
                    }
                }
            }
        } finally {
            pose.popMatrix()
        }
    }

    fun render(partialTicks: Float, isEditing: Boolean = false) {
        if (!isInitialized) initConfig()
        clampElementsToViewport()

        HUDModule.withHudFont {
            if (HUDModule.showCrosshair.value) {
                HUDModule.crosshair.render()
            }

            for (element in elements) {
                if (element.enabled) {
                    val s = element.scale
                    if (s != 1f) {
                        val transform = NanoVGTransform(Draw2D.context)
                        transform.withTransform {
                            transform.translate(element.x, element.y)
                            transform.scale(s)
                            transform.translate(-element.x, -element.y)
                            element.render(partialTicks, isEditing)
                        }
                    } else {
                        element.render(partialTicks, isEditing)
                    }
                    clampElementToViewport(element)
                }
            }

            if (isEditing) {
                this.renderEditOverlay(partialTicks)
            }
        }

        NotificationManager.render()
    }

}
