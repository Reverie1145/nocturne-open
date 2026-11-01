package cn.reverie.manager

import cn.reverie.app.command.CommandManager
import cn.reverie.app.event.EventBus
import cn.reverie.app.event.input.MouseInputEvent
import cn.reverie.gui.mainmenu.LoginScreen
import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.gui.splash.ProtectionSplashWindow
import cn.reverie.gui.vanilla.ServerListEntryPainter
import cn.reverie.gui.vanilla.VanillaListScreenRenderer
import cn.reverie.gui.vanilla.WorldListEntryPainter
import cn.reverie.module.render.ChatBarModule
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.render.ChatScreenAccess
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.ui.NvgScreen
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.network.chat.Component
import java.util.WeakHashMap

object GuiHookManager {
    private val vanillaListRenderers = WeakHashMap<Screen, VanillaListScreenRenderer>()
    @JvmStatic fun authenticatedMenu(): Screen = if (NocturneAuth.isAuthenticated) MainMenuScreen() else LoginScreen()
    @JvmStatic fun isAuthenticated(): Boolean = NocturneAuth.isAuthenticated
    @JvmStatic fun finishProtectionSplash() = ProtectionSplashWindow.finish()

    @JvmStatic fun shouldReplaceVanillaChatRenderer(): Boolean = ChatBarModule.shouldReplaceVanillaRenderer()
    @JvmStatic fun styleChatBackground(color: Int): Int = ChatBarModule.styleVanillaBackground(color)
    @JvmStatic fun shouldReplaceChatInput(screen: Screen?, input: Any): Boolean =
        ChatBarModule.shouldReplaceVanillaRenderer() && screen is ChatScreen &&
            (screen as? ChatScreenAccess)?.let { it.`nocturne$getInput`() == input } == true
    @JvmStatic fun onChatImageDoubleClick(x: Double, y: Double): Boolean =
        ChatBarModule.onImageDoubleClick(ResolutionHelper.toCanvasX(x), ResolutionHelper.toCanvasY(y))

    @JvmStatic fun animateClientPrefix(message: net.minecraft.util.FormattedCharSequence) = ChatUtil.animateNocturnePrefix(message)
    @JvmStatic fun executeCommand(command: String) = CommandManager.execute(command)
    @JvmStatic fun publishAgentChat(message: String, direction: String) =
        cn.reverie.app.agent.AgentManager.onChat(message, direction)
    @JvmStatic fun commandDispatcher(): CommandDispatcher<ClientSuggestionProvider> = CommandManager.dispatcher

    @JvmStatic fun isNvgScreen(screen: Screen?): Boolean = screen is NvgScreen
    @JvmStatic fun onNvgKeyPressed(screen: Screen?, event: KeyEvent): Boolean =
        (screen as? NvgScreen)?.onKeyPressed(event) == true
    @JvmStatic fun onNvgCharTyped(screen: Screen?, event: CharacterEvent): Boolean =
        (screen as? NvgScreen)?.onCharTyped(event) == true
    @JvmStatic fun onNvgMouseClicked(screen: Screen?, event: MouseButtonEvent, doubleClick: Boolean): Boolean =
        (screen as? NvgScreen)?.onMouseClicked(event, doubleClick) == true
    @JvmStatic fun onNvgMouseReleased(screen: Screen?, event: MouseButtonEvent): Boolean =
        (screen as? NvgScreen)?.onMouseReleased(event) == true
    @JvmStatic fun onNvgMouseScrolled(
        screen: Screen?, x: Double, y: Double, scrollX: Double, scrollY: Double
    ): Boolean = (screen as? NvgScreen)?.onMouseScrolled(x, y, scrollX, scrollY) == true

    @JvmStatic
    fun publishMouseInput(event: MouseButtonEvent, action: Int, screen: Screen?): Boolean {
        val inputEvent = MouseInputEvent(event, action, screen)
        EventBus.post(inputEvent)
        return inputEvent.isCancelled
    }

    private fun rendererFor(screen: Screen): VanillaListScreenRenderer? {
        vanillaListRenderers[screen]?.let { return it }
        val renderer = when (screen) {
            is SelectWorldScreen -> VanillaListScreenRenderer(screen, WorldListEntryPainter(), Component.translatable("selectWorld.search"))
            is JoinMultiplayerScreen -> VanillaListScreenRenderer(screen, ServerListEntryPainter(), null)
            else -> return null
        }
        renderer.resetForOpen()
        vanillaListRenderers[screen] = renderer
        return renderer
    }

    @JvmStatic fun renderVanillaList(screen: Screen, mouseX: Float, mouseY: Float, partialTicks: Float) =
        rendererFor(screen)?.render(mouseX, mouseY, partialTicks)
    @JvmStatic fun vanillaListMouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean) =
        rendererFor(screen)?.onMouseClicked(event, doubleClick) == true
    @JvmStatic fun vanillaListMouseReleased(screen: Screen, event: MouseButtonEvent) =
        rendererFor(screen)?.onMouseReleased(event) == true
    @JvmStatic fun vanillaListMouseDragged(screen: Screen, event: MouseButtonEvent, dx: Double, dy: Double) =
        rendererFor(screen)?.onMouseDragged(event, dx, dy) == true
    @JvmStatic fun vanillaListMouseScrolled(screen: Screen, x: Double, y: Double, scrollX: Double, scrollY: Double) =
        rendererFor(screen)?.onMouseScrolled(x, y, scrollX, scrollY) == true
    @JvmStatic fun vanillaListKeyPressed(screen: Screen, event: KeyEvent) =
        rendererFor(screen)?.onKeyPressed(event) == true
    @JvmStatic fun vanillaListCharTyped(screen: Screen, event: CharacterEvent) =
        rendererFor(screen)?.onCharTyped(event) == true
}
