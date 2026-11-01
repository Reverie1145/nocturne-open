package cn.reverie.gui.mainmenu

import cn.reverie.gui.clickgui.ClickGuiScreen
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.screens.AccountManagerScreen
import cn.reverie.render.video.VideoBackgroundPlayer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen
import net.minecraft.client.gui.screens.options.LanguageSelectScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW

/**
 * Nocturne main menu.
 *
 * A single title screen drawn over the cherry-blossom footage: the wordmark and the two ways to play on the left,
 * a magnifying dock with the rest of the client on the right and language / accessibility / account in the top bar.
 * There is nothing to scroll; the motion is in the entrance, the hover response and the camera push that carries
 * the player into the next screen.
 *
 * Keyboard: Left / Right / Tab move focus, Enter activates the focused element (the first press only shows the
 * focus ring, so Quit is never one key away).
 */
class MainMenuScreen : TitleScreen(false) {

    internal val entranceAnim = FloatAnimation(0f, 1f, 1700L, CubicBezier.EASE_OUT)

    internal inner class MenuItem(
        val title: String,
        val subtitle: String,
        val iconName: String,
        val tint: NanoVGColor,
        val isDanger: Boolean = false,
        val onClick: () -> Unit
    ) {
        val hoverAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
        val pressAnim = FloatAnimation(0f, 0f, 120L, CubicBezier.EASE_OUT)
        var isPressed = false

        /** Pointer position inside the item, -1..1 on both axes, smoothed. Drives magnetism, tilt and glare. */
        var tiltX = 0f
        var tiltY = 0f

        /** Dock icons only: current magnification, 1 = resting size. */
        var dockMag = 1f

        /** Hit rectangle, refreshed every frame the item is drawn (w == 0 means "not on screen"). */
        var x = 0f
        var y = 0f
        var w = 0f
        var h = 0f

        fun contains(mx: Float, my: Float): Boolean =
            w > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h

        fun clearBounds() {
            w = 0f
            h = 0f
        }
    }

    private val mc: Minecraft get() = Minecraft.getInstance()

    private fun openSingleplayer() = mc.gui.setScreen(SelectWorldScreen(this))
    private fun openMultiplayer() = mc.gui.setScreen(JoinMultiplayerScreen(this))

    internal val heroPlay = MenuItem("Singleplayer", "Play offline", "singleplay.png", MenuFx.SAKURA) { openSingleplayer() }
    internal val heroMulti = MenuItem("Multiplayer", "Join a server", "multiplay.png", MenuFx.PEACH) { openMultiplayer() }

    internal val dockAlt = MenuItem("Alt Manager", "Accounts", "account.png", MenuFx.SAKURA) {
        mc.gui.setScreen(AccountManagerScreen(this))
    }
    internal val dockClick = MenuItem("ClickGUI", "Modules", "scripts.png", MenuFx.VIOLET) {
        mc.gui.setScreen(ClickGuiScreen())
    }
    internal val dockMusic = MenuItem("Music", "Cloud player", "play.png", MenuFx.PEACH) {
        mc.gui.setScreen(cn.reverie.app.music.ui.CloudMusicScreen(this))
    }
    internal val dockAgent = MenuItem("AI Agent", "Scripts", "agi.png", MenuFx.AQUA) {
        cn.reverie.app.agent.AgentManager.open()
    }
    internal val dockSettings = MenuItem("Settings", "Game options", "settings.png", MenuFx.SLATE) {
        mc.gui.setScreen(OptionsScreen(this, mc.options, false))
    }
    internal val dockQuit = MenuItem("Quit", "Close the client", "off.png", MenuFx.DANGER, isDanger = true) {
        mc.stop()
    }

    /** Reading order; Left / Right / Tab walk this list. */
    internal val items: List<MenuItem> = listOf(
        heroPlay, heroMulti,
        dockAlt, dockClick, dockMusic, dockAgent, dockSettings, dockQuit
    )

    /** Top-bar shortcuts, reported by [MenuChrome] as QUICK_BASE + index. */
    internal class QuickAction(val tooltip: String, val iconName: String, val run: () -> Unit)

    internal val quickActions: List<QuickAction> = listOf(
        QuickAction("Language", "world.png") {
            mc.gui.setScreen(LanguageSelectScreen(this, mc.options, mc.languageManager))
        },
        QuickAction("Accessibility", "man.png") {
            mc.gui.setScreen(AccessibilityOptionsScreen(this, mc.options))
        },
        QuickAction("Switch account", "signout") {
            NocturneAuth.logout()
            mc.gui.setScreen(LoginScreen())
        }
    )

    // -----------------------------------------------------------------------------------------
    // Scene state (shared with the child screens through MenuScene)
    // -----------------------------------------------------------------------------------------

    internal var scroll: Float
        get() = MenuScene.scroll
        set(v) { MenuScene.scroll = v }

    internal var parX: Float
        get() = MenuScene.parX
        set(v) { MenuScene.parX = v }
    internal var parY: Float
        get() = MenuScene.parY
        set(v) { MenuScene.parY = v }

    internal var lastFrameNs = 0L

    /** Action waiting for the exit animation to finish, and when that animation started (0 = not leaving). */
    internal var pendingAction: (() -> Unit)? = null
    internal var exitStartMs = 0L

    /** Plays the exit animation (a camera push into the footage), then runs [action]. */
    internal fun launch(action: () -> Unit) {
        if (pendingAction != null) return
        pendingAction = action
        exitStartMs = System.currentTimeMillis()
        for (item in items) item.isPressed = false
    }

    internal var focusItem: MenuItem? = null
    internal var showFocus = false

    internal var lastMouseX = Float.NaN
    internal var lastMouseY = Float.NaN
    internal var enterStartMs = 0L

    private fun moveFocus(delta: Int) {
        val current = items.indexOf(focusItem)
        val from = if (current >= 0) current else if (delta > 0) -1 else 0
        focusItem = items[(from + delta + items.size) % items.size]
        showFocus = true
    }

    override fun init() {
        val returning = MenuScene.introPlayed
        // Coming back from a child screen: the scene is already on screen, so only the content settles in again.
        entranceAnim.durationMs = if (returning) 700L else 1700L
        entranceAnim.setImmediate(0f)
        entranceAnim.animateTo(1f)
        val now = System.currentTimeMillis()
        enterStartMs = if (returning) now - 1500L else now
        lastFrameNs = 0L
        pendingAction = null
        exitStartMs = 0L
        MenuScene.mainMenu = this
        MenuScene.pushTarget = 0f
        showFocus = false
        lastMouseX = Float.NaN
        lastMouseY = Float.NaN
        for (item in items) {
            item.hoverAnim.setImmediate(0f)
            item.pressAnim.setImmediate(0f)
            item.isPressed = false
            item.tiltX = 0f
            item.tiltY = 0f
        }
        focusItem = null
        VideoBackgroundPlayer.init()
    }

    override fun removed() {
        MenuScene.pushTarget = 0f
        super.removed()
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun isPauseScreen(): Boolean = false
    override fun shouldCloseOnEsc(): Boolean = false

    fun renderMainMenu(mouseX: Float, mouseY: Float, partialTicks: Float) = this.renderMainMenuImpl(mouseX, mouseY, partialTicks)

    // -----------------------------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x().toFloat())
        val my = ResolutionHelper.toCanvasY(event.y().toFloat())

        if (pendingAction != null) return true

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            val chip = MenuChrome.hitTest(mx, my)
            if (chip == MenuChrome.ACCOUNT) {
                launch { dockAlt.onClick() }
                return true
            }
            if (chip >= MenuChrome.QUICK_BASE) {
                quickActions.getOrNull(chip - MenuChrome.QUICK_BASE)?.let { launch(it.run) }
                return true
            }
            for (item in items.asReversed()) {
                if (item.contains(mx, my)) {
                    item.isPressed = true
                    focusItem = item
                    return true
                }
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x().toFloat())
        val my = ResolutionHelper.toCanvasY(event.y().toFloat())

        for (item in items) {
            if (item.isPressed) {
                item.isPressed = false
                if (item.contains(mx, my)) {
                    launch(item.onClick)
                    return true
                }
            }
        }
        return super.mouseReleased(event)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        when (event.key()) {
            GLFW.GLFW_KEY_ESCAPE -> return true
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_W -> {
                moveFocus(-1)
                return true
            }
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_S -> {
                moveFocus(1)
                return true
            }
            GLFW.GLFW_KEY_TAB -> {
                moveFocus(if (event.modifiers() and GLFW.GLFW_MOD_SHIFT != 0) -1 else 1)
                return true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                val target = focusItem
                if (!showFocus || target == null) {
                    moveFocus(1)
                } else {
                    launch(target.onClick)
                }
                return true
            }
        }
        return super.keyPressed(event)
    }
}
