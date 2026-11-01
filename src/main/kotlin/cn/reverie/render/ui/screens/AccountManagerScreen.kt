package cn.reverie.render.ui.screens

import cn.reverie.app.account.Account
import cn.reverie.app.account.AccountManager
import cn.reverie.app.account.MicrosoftAuth
import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.gui.mainmenu.MenuScene
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.ui.NvgScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Accounts: the "Toolkit" child screen of the Nocturne main menu.
 *
 * Shares the background scene, navigation, page rail and motion with the main menu. Login methods (ported from
 * OpenMyau-Plus): Microsoft in the browser, Microsoft device code, offline, cookie file, refresh/access token and
 * `ign:uuid:token` sessions. State and input live here; drawing is in AccountManagerRenderers.kt and the input and
 * login flows in AccountManagerInput.kt.
 */
class AccountManagerScreen(val parent: Screen? = null) :
    Screen(Component.literal("Account Manager")), NvgScreen {

    internal enum class Modal { NONE, OFFLINE, MS_BROWSER, MS_DEVICE, COOKIE, TOKEN, SESSION }

    internal data class RectF(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun contains(px: Float, py: Float): Boolean =
            px >= x && px <= x + width && py >= y && py <= y + height
    }

    internal class Pill(
        var label: String,
        val isPrimary: Boolean = false,
        val isDanger: Boolean = false,
        val needsSelection: Boolean = false,
        val onClick: () -> Unit
    ) {
        val hoverAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
        val pressAnim = FloatAnimation(0f, 0f, 100L, CubicBezier.EASE_OUT)
        var isPressed = false
        var isEnabled = true
        var x = 0f
        var y = 0f
        var w = 0f
        var h = 0f

        fun contains(mx: Float, my: Float): Boolean = w > 0f && mx >= x && mx <= x + w && my >= y && my <= y + h
    }

    internal class RowHit(val account: Account, val rect: RectF, val quick: RectF)

    // Motion
    internal var enterMs = 0L
    internal var closeStartMs = 0L
    internal var pendingClose: (() -> Unit)? = null
    internal var lastFrameNs = 0L

    // List
    internal var scrollPos = 0f
    internal var scrollTarget = 0f
    internal var scrollMax = 0f
    internal var scrollbarThumb: RectF? = null
    internal var draggingScrollbar = false
    internal var dragGrabOffset = 0f
    internal var listRect = RectF(0f, 0f, 0f, 0f)
    internal var visible: List<Account> = emptyList()
    internal val visibleRows = ArrayList<RowHit>()
    internal val cardHover = HashMap<String, Float>()
    internal var selectedId: String? = null
    internal var lastClickedId: String? = null
    internal var lastClickTime = 0L

    // Search
    internal val searchFocusAnim = FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT)
    internal var searchQuery = ""
    internal var searchFocused = false
    internal var searchBoxRect = RectF(0f, 0f, 0f, 0f)
    internal var searchClearRect = RectF(0f, 0f, 0f, 0f)
    internal var cursorTimer = 0L

    // Detail panel
    internal var autoLoginRect = RectF(0f, 0f, 0f, 0f)
    internal val autoLoginAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)

    // Modal
    internal var modal = Modal.NONE
    internal var lastModal = Modal.NONE
    internal val modalAnim = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
    internal var modalInput = ""
    internal var modalSummary = ""
    internal var modalFocused = false
    internal var modalError = ""
    internal var modalStatus = ""
    internal var modalBusy = false
    internal var modalGeneration = 0
    internal var modalInputRect = RectF(0f, 0f, 0f, 0f)
    internal var modalCardRect = RectF(0f, 0f, 0f, 0f)
    internal val modalLeft = ArrayList<Pill>()
    internal val modalRight = ArrayList<Pill>()

    internal var browserSession: MicrosoftAuth.BrowserSession? = null
    internal var deviceSession: MicrosoftAuth.AuthSession? = null
    internal var deviceCode = ""
    internal var deviceUri = MicrosoftAuth.DEFAULT_VERIFICATION_URI
    internal var linkCopied = false

    // Buttons
    internal val backButton = Pill("Back") { requestClose { closeScreen() } }
    internal val toolbarButtons = ArrayList<Pill>()
    internal val loginButton = Pill("Log in", isPrimary = true, needsSelection = true) { loginSelected() }
    internal val copyButton = Pill("Copy name", needsSelection = true) { copySelectedName() }
    internal val removeButton = Pill("Remove", isDanger = true, needsSelection = true) { removeSelected() }

    internal val mc: Minecraft get() = Minecraft.getInstance()

    /** In-game the menu chrome (tabs and rail) is hidden; the account list itself works the same. */
    internal val inMenu: Boolean get() = mc.level == null

    internal val selected: Account? get() = AccountManager.findAccount(selectedId)

    init {
        buildToolbar()
    }

    private fun buildToolbar() {
        toolbarButtons.clear()
        toolbarButtons.add(Pill("Microsoft", isPrimary = true) { openModal(Modal.MS_BROWSER) })
        toolbarButtons.add(Pill("Offline") { openModal(Modal.OFFLINE) })
        toolbarButtons.add(Pill("Cookie") { openModal(Modal.COOKIE) })
        toolbarButtons.add(Pill("Token") { openModal(Modal.TOKEN) })
        toolbarButtons.add(Pill("Session") { openModal(Modal.SESSION) })
        toolbarButtons.add(Pill("Random") {
            val acc = AccountManager.loginRandomOffline()
            if (acc != null) {
                selectedId = acc.id
                AccountManager.status("Created random alt: ${acc.name}")
            }
        })
    }

    override fun init() {
        super.init()
        enterMs = System.currentTimeMillis()
        closeStartMs = 0L
        pendingClose = null
        lastFrameNs = 0L
        scrollPos = 0f
        scrollTarget = 0f
        cardHover.clear()
        draggingScrollbar = false
        searchFocused = false
        searchQuery = ""
        cursorTimer = System.currentTimeMillis()
        autoLoginAnim.setImmediate(if (AccountManager.autoLogin) 1f else 0f)
        if (selectedId == null || selected == null) selectedId = AccountManager.currentAccount?.id
        buildToolbar()
    }

    override fun isPauseScreen(): Boolean = false

    /** Plays the departure motion, then runs [action]. */
    internal fun requestClose(action: () -> Unit) {
        if (closeStartMs > 0L) return
        pendingClose = action
        closeStartMs = System.currentTimeMillis()
        searchFocused = false
        modalFocused = false
        for (b in toolbarButtons) b.isPressed = false
    }

    internal fun closeScreen() {
        mc.gui.setScreen(parent ?: if (mc.level == null) MainMenuScreen() else null)
    }

    override fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float) = renderAccountManager(mouseX, mouseY)

    override fun onMouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean = handleMouseClicked(event)
    override fun onMouseReleased(event: MouseButtonEvent): Boolean = handleMouseReleased()
    override fun onMouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean = handleMouseDragged(event)
    override fun onMouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean = handleMouseScrolled(scrollY)
    override fun onKeyPressed(event: KeyEvent): Boolean = handleKeyPressed(event)
    override fun onCharTyped(event: CharacterEvent): Boolean = handleCharTyped(event)

    override fun onClose() {
        cancelSessions()
        requestClose { closeScreen() }
    }

    override fun removed() {
        modalGeneration++
        cancelSessions()
        cn.reverie.module.misc.AccountManagerModule.onScreenClosed()
        super.removed()
    }
}
