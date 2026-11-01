package cn.reverie.render.ui

import cn.reverie.render.animation.Transition
import cn.reverie.render.animation.TransitionType
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.nanovg.NanoVGColor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

abstract class ModernScreen(
    title: Component = Component.literal("Nocturne Screen"),
    val enableBackdropBlur: Boolean = true,
    val blurRadius: Float = 16.0f,
    val transitionType: TransitionType = TransitionType.SCALE_UP
) : Screen(title) {

    val rootContainer = UIContainer()
    val transition = Transition(transitionType, 250L)
    var theme: UITheme = UITheme.DARK

    override fun init() {
        super.init()
        ResolutionHelper.syncWithMinecraft()
        rootContainer.x = 0f
        rootContainer.y = 0f
        rootContainer.width = ResolutionHelper.scaledWidth
        rootContainer.height = ResolutionHelper.scaledHeight
        transition.open()
        buildUI()
    }

    abstract fun buildUI()

    override fun tick() {
        super.tick()
        rootContainer.onTick()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {

    }

    open fun renderModernScreen(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val mx = mouseX
        val my = mouseY

        if (enableBackdropBlur) {
            RenderEngine.blurEngine.backdropRenderer.renderBackdrop(
                rootContainer.bounds,
                radius = 0f,
                tintColor = NanoVGColor(0.05f, 0.05f, 0.08f, 0.65f),
                blurRadius = blurRadius
            )
        }

        rootContainer.render(mx, my, partialTicks)
    }

    override fun mouseClicked(event: net.minecraft.client.input.MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x).toFloat()
        val my = ResolutionHelper.toCanvasY(event.y).toFloat()
        if (rootContainer.mouseClicked(mx, my, event.button())) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: net.minecraft.client.input.MouseButtonEvent): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x).toFloat()
        val my = ResolutionHelper.toCanvasY(event.y).toFloat()
        if (rootContainer.mouseReleased(mx, my, event.button())) return true
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: net.minecraft.client.input.MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x).toFloat()
        val my = ResolutionHelper.toCanvasY(event.y).toFloat()
        val dragX = ResolutionHelper.toCanvasDelta(dx).toFloat()
        val dragY = ResolutionHelper.toCanvasDelta(dy).toFloat()
        if (rootContainer.mouseDragged(mx, my, event.button(), dragX, dragY)) return true
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val mx = ResolutionHelper.toCanvasX(x).toFloat()
        val my = ResolutionHelper.toCanvasY(y).toFloat()
        if (rootContainer.mouseScrolled(mx, my, scrollX.toFloat(), scrollY.toFloat())) return true
        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: net.minecraft.client.input.KeyEvent): Boolean {
        if (rootContainer.keyPressed(event.key, event.scancode, event.modifiers)) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: net.minecraft.client.input.CharacterEvent): Boolean {
        if (event.codepoint > 0 && rootContainer.charTyped(event.codepoint.toChar(), 0)) return true
        return super.charTyped(event)
    }

    override fun onClose() {
        transition.close {
            super.onClose()
        }
    }
}
