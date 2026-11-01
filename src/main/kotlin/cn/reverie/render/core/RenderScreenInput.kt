package cn.reverie.render.core

import cn.reverie.gui.clickgui.ClickGuiScreen
import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.render.ui.ModernScreen
import cn.reverie.render.ui.NvgScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.opengl.GL11C.*
import org.lwjgl.opengl.GL30C.*
import cn.reverie.gui.clickgui.window.WindowClickGuiScreen
/** Screen-specific input and render-state forwarding kept out of the render pipeline. */
internal object RenderScreenInput {
    fun onRenderScreenPre(screen: Screen): Boolean =
        screen is ClickGuiScreen || screen is WindowClickGuiScreen || screen is MainMenuScreen ||
            screen is ModernScreen || screen is NvgScreen

    fun onRenderScreenPost(engine: RenderEngine, screen: Screen, graphics: GuiGraphicsExtractor) {
        if (engine.isInitialized && screen is ChatScreen) {
            engine.hudRenderer.extractRenderState(graphics, Minecraft.getInstance().deltaTracker)
        }
    }

    fun onMouseClicked(engine: RenderEngine, screen: Screen?, x: Double, y: Double, button: Int): Boolean =
        engine.isInitialized && screen is ChatScreen && engine.hudRenderer.onMouseClick(
            ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat(), button
        )

    fun onMouseReleased(engine: RenderEngine, screen: Screen?, x: Double, y: Double, button: Int): Boolean =
        engine.isInitialized && screen is ChatScreen && engine.hudRenderer.onMouseRelease(
            ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat(), button
        )

    fun onMouseDragged(engine: RenderEngine, screen: Screen?, x: Double, y: Double): Boolean {
        if (!engine.isInitialized || screen !is ChatScreen) return false
        engine.hudRenderer.onMouseDrag(ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat())
        return true
    }

    fun onMouseMoved(engine: RenderEngine, screen: Screen?, x: Double, y: Double) {
        if (engine.isInitialized && screen is ChatScreen) {
            engine.hudRenderer.onMouseMove(ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat())
        }
    }

    fun onMouseScrolled(engine: RenderEngine, screen: Screen, x: Double, y: Double, scrollY: Double): Boolean =
        engine.isInitialized && screen is ChatScreen &&
            engine.hudRenderer.onMouseScrolled(
                ResolutionHelper.toCanvasX(x).toFloat(), ResolutionHelper.toCanvasY(y).toFloat(), scrollY.toFloat()
            )

    fun onKeyPressed(engine: RenderEngine, screen: Screen, keyCode: Int, modifiers: Int): Boolean =
        engine.isInitialized && screen is ChatScreen && engine.hudRenderer.onKeyPressed(keyCode, modifiers)

    fun onCharTyped(): Boolean = false

    fun onScreenClosed(screen: Screen) {
        (screen as? ModernScreen)?.onClose()
    }
}
