package cn.reverie.render.ui.screens

import cn.reverie.app.account.Account
import cn.reverie.app.account.AccountManager
import cn.reverie.app.account.CookieParser
import cn.reverie.app.account.MicrosoftAuth
import cn.reverie.gui.mainmenu.MainMenuScreen
import cn.reverie.gui.mainmenu.MenuChrome
import cn.reverie.gui.vanilla.ROW_GAP
import cn.reverie.gui.vanilla.ROW_H
import cn.reverie.gui.vanilla.ROW_PAD
import cn.reverie.render.ui.screens.AccountManagerScreen.Modal
import cn.reverie.render.ui.screens.AccountManagerScreen.Pill
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

private const val COOKIE_CAP = 200_000
private const val TOKEN_CAP = 8_192

internal fun Modal.takesInput(): Boolean = this == Modal.OFFLINE || this == Modal.TOKEN || this == Modal.SESSION || this == Modal.COOKIE

// ---------------------------------------------------------------------------------------------
// Modal lifecycle
// ---------------------------------------------------------------------------------------------

internal fun AccountManagerScreen.cancelSessions() {
    browserSession?.cancel()
    browserSession = null
    deviceSession?.cancel()
    deviceSession = null
}

internal fun AccountManagerScreen.closeModal() {
    modal = Modal.NONE
    modalGeneration++
    cancelSessions()
    modalFocused = false
    modalBusy = false
    modalAnim.animateTo(0f)
}

internal fun AccountManagerScreen.openModal(type: Modal) {
    cancelSessions()
    modalGeneration++
    modal = type
    lastModal = type
    modalInput = ""
    modalSummary = ""
    modalError = ""
    modalStatus = ""
    modalBusy = false
    linkCopied = false
    modalFocused = type.takesInput()
    searchFocused = false
    cursorTimer = System.currentTimeMillis()
    modalLeft.clear()
    modalRight.clear()
    modalAnim.setImmediate(0f)
    modalAnim.animateTo(1f)

    val cancel = Pill("Cancel") { closeModal() }
    when (type) {
        Modal.OFFLINE -> {
            modalLeft.add(Pill("Random") { setModalInput(AccountManager.randomName()) })
            modalRight.add(cancel)
            modalRight.add(Pill("Sign in", isPrimary = true) { confirmModal() })
        }
        Modal.TOKEN, Modal.SESSION -> {
            modalLeft.add(Pill("Paste") { pasteIntoModal() })
            modalRight.add(cancel)
            modalRight.add(Pill("Log in", isPrimary = true) { confirmModal() })
        }
        Modal.COOKIE -> {
            modalLeft.add(Pill("Choose file") { chooseCookieFile() })
            modalLeft.add(Pill("Paste") { pasteIntoModal() })
            modalRight.add(cancel)
            modalRight.add(Pill("Log in", isPrimary = true) { confirmModal() })
        }
        Modal.MS_BROWSER -> startBrowserLogin()
        Modal.MS_DEVICE -> startDeviceLogin()
        Modal.NONE -> {}
    }
}

private fun AccountManagerScreen.startBrowserLogin() {
    modalStatus = "Starting sign-in..."
    val session = MicrosoftAuth.startBrowserFlow { modalStatus = it }
    browserSession = session
    mc.keyboardHandler.clipboard = session.link
    linkCopied = true
    modalStatus = "Login link copied to the clipboard. Open it in your browser."

    modalLeft.add(Pill("Open browser") { openUri(session.link) })
    modalLeft.add(Pill("Copy link") {
        mc.keyboardHandler.clipboard = session.link
        linkCopied = true
        modalStatus = "Login link copied to the clipboard."
    })
    modalRight.add(Pill("Device code") { openModal(Modal.MS_DEVICE) })
    modalRight.add(Pill("Cancel") { closeModal() })
    runLogin(session.future, "Microsoft sign-in")
}

private fun AccountManagerScreen.startDeviceLogin() {
    deviceCode = "..."
    deviceUri = MicrosoftAuth.DEFAULT_VERIFICATION_URI
    modalStatus = "Requesting a device code from Microsoft..."
    val session = MicrosoftAuth.startDeviceCodeFlow(
        onDeviceCode = { result ->
            deviceCode = result.userCode
            deviceUri = result.verificationUri
            modalStatus = "Go to ${result.verificationUri} and enter the code."
        },
        onStatusUpdate = { modalStatus = it }
    )
    deviceSession = session

    modalLeft.add(Pill("Copy and open") {
        mc.keyboardHandler.clipboard = deviceCode
        openUri(deviceUri)
        linkCopied = true
        AccountManager.status("Device code copied: $deviceCode")
    })
    modalRight.add(Pill("Browser") { openModal(Modal.MS_BROWSER) })
    modalRight.add(Pill("Cancel") { closeModal() })
    runLogin(session.future, "Microsoft sign-in")
}

private fun openUri(uri: String) {
    try {
        Util.getPlatform().openUri(uri)
    } catch (_: Throwable) {
    }
}

// ---------------------------------------------------------------------------------------------
// Login flows
// ---------------------------------------------------------------------------------------------

private fun unwrap(error: Throwable?): Throwable? =
    if (error is CompletionException || error is ExecutionException) error.cause ?: error else error

/** Waits for an auth future; on success stores the account and signs in, on failure shows the reason in the modal. */
internal fun AccountManagerScreen.runLogin(future: CompletableFuture<Account>, label: String) {
    modalBusy = true
    modalError = ""
    val generation = modalGeneration
    future.whenComplete { account, error ->
        mc.execute {
            if (generation != modalGeneration) return@execute
            modalBusy = false
            if (error != null || account == null) {
                val message = unwrap(error)?.message?.takeIf { it.isNotBlank() } ?: "$label failed"
                modalError = message
                modalStatus = ""
                AccountManager.status("$label failed: $message")
            } else {
                finishLogin(account)
            }
        }
    }
}

internal fun AccountManagerScreen.finishLogin(account: Account) {
    AccountManager.addAccount(account)
    AccountManager.login(account)
    selectedId = account.id
    closeModal()
}

internal fun AccountManagerScreen.confirmModal() {
    if (modalBusy) return
    val text = modalInput.trim()
    when (modal) {
        Modal.OFFLINE -> when {
            text.isEmpty() -> modalError = "Username cannot be empty"
            !Account.isValidName(text) -> modalError = "3-16 characters: letters, digits or _"
            else -> {
                AccountManager.loginOffline(text)?.let { selectedId = it.id }
                closeModal()
            }
        }
        Modal.TOKEN -> {
            if (text.isEmpty()) { modalError = "Token cannot be empty"; return }
            modalFocused = false
            modalStatus = "Checking token..."
            runLogin(MicrosoftAuth.importToken(text) { modalStatus = it }, "Token login")
        }
        Modal.SESSION -> {
            if (text.isEmpty()) { modalError = "Paste ign:uuid:token or an access token"; return }
            modalFocused = false
            modalStatus = "Checking session..."
            runLogin(MicrosoftAuth.importSession(text), "Session login")
        }
        Modal.COOKIE -> {
            if (text.isEmpty()) { modalError = "Load a cookie file or paste cookies first"; return }
            modalFocused = false
            modalStatus = "Authenticating with cookies..."
            runLogin(MicrosoftAuth.loginWithCookies(modalInput) { modalStatus = it }, "Cookie login")
        }
        else -> {}
    }
}

internal fun AccountManagerScreen.loginSelected() {
    selected?.let { AccountManager.login(it) }
}

internal fun AccountManagerScreen.copySelectedName() {
    val account = selected ?: return
    mc.keyboardHandler.clipboard = account.name
    AccountManager.status("Copied ${account.name}")
}

internal fun AccountManagerScreen.removeSelected() {
    val account = selected ?: return
    val index = visible.indexOfFirst { it.id == account.id }
    AccountManager.removeAccount(account)
    AccountManager.status("Removed account ${account.name}")
    val next = AccountManager.filtered(searchQuery)
    selectedId = next.getOrNull(index.coerceIn(0, (next.size - 1).coerceAtLeast(0)))?.id
}

// ---------------------------------------------------------------------------------------------
// Modal text input
// ---------------------------------------------------------------------------------------------

internal fun AccountManagerScreen.setModalInput(text: String) {
    modalInput = text
    modalError = ""
    modalSummary = when (modal) {
        Modal.COOKIE -> if (text.isEmpty()) "" else "${text.length} characters  \u00b7  ${CookieParser.parse(text).size} cookies found"
        Modal.TOKEN, Modal.SESSION -> if (text.isEmpty()) "" else "\u2022".repeat(text.length.coerceAtMost(26)) + "  (${text.length} characters)"
        else -> text
    }
}

internal fun AccountManagerScreen.pasteIntoModal() {
    if (modal == Modal.OFFLINE || !modal.takesInput()) return
    val clip = mc.keyboardHandler.clipboard
    if (clip.isEmpty()) return
    if (modal == Modal.COOKIE) {
        setModalInput(clip.take(COOKIE_CAP))
    } else {
        setModalInput(clip.filterNot { it.isWhitespace() }.take(TOKEN_CAP))
    }
    modalFocused = true
}

/** Native file dialog through LWJGL's tinyfd (looked up reflectively so a missing module cannot break the build). */
internal fun AccountManagerScreen.chooseCookieFile() {
    if (modalBusy) return
    modalStatus = "Opening the file chooser..."
    Thread {
        var unavailable = false
        val chosen: String? = try {
            val cls = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs")
            val open = cls.getMethod(
                "tinyfd_openFileDialog",
                CharSequence::class.java, CharSequence::class.java, org.lwjgl.PointerBuffer::class.java,
                CharSequence::class.java, java.lang.Boolean.TYPE
            )
            open.invoke(null, "Choose a cookie file", "", null, "Cookie files", false) as String?
        } catch (_: Throwable) {
            unavailable = true
            null
        }

        mc.execute {
            if (modal != Modal.COOKIE) return@execute
            when {
                unavailable -> modalStatus = "No file chooser here. Paste the cookies instead."
                chosen.isNullOrBlank() -> modalStatus = "No file selected."
                else -> try {
                    val file = File(chosen)
                    setModalInput(file.readText(Charsets.UTF_8).take(COOKIE_CAP))
                    modalStatus = "Loaded ${file.name}"
                } catch (e: Throwable) {
                    modalError = "Could not read file: ${e.message}"
                    modalStatus = ""
                }
            }
        }
    }.apply {
        isDaemon = true
        name = "Nocturne-Cookie-Chooser"
        start()
    }
}

// ---------------------------------------------------------------------------------------------
// Pointer input (coordinates already arrive in canvas space, see MixinMouseHandler)
// ---------------------------------------------------------------------------------------------

private fun AccountManagerScreen.press(p: Pill): Boolean {
    if (!p.isEnabled) return false
    p.isPressed = true
    p.onClick()
    return true
}

internal fun AccountManagerScreen.handleMouseClicked(event: MouseButtonEvent): Boolean {
    val mx = event.x().toFloat()
    val my = event.y().toFloat()
    val btn = event.button()

    if (closeStartMs > 0L) return true

    if (modal != Modal.NONE) {
        if (btn != 0) return true
        if (!modalCardRect.contains(mx, my)) {
            closeModal()
            return true
        }
        for (p in modalLeft + modalRight) if (p.contains(mx, my) && press(p)) return true
        modalFocused = modal.takesInput() && modalInputRect.contains(mx, my)
        if (modalFocused) cursorTimer = System.currentTimeMillis()
        return true
    }
    if (btn != 0) return false

    val chip = MenuChrome.hitTest(mx, my)
    if (chip == MenuChrome.BRAND) {
        requestClose { closeScreen() }
        return true
    }

    if (backButton.contains(mx, my)) return press(backButton)
    for (tb in toolbarButtons) if (tb.contains(mx, my) && press(tb)) return true
    for (p in listOf(loginButton, copyButton, removeButton)) if (p.contains(mx, my) && press(p)) return true

    if (autoLoginRect.contains(mx, my)) {
        AccountManager.autoLogin = !AccountManager.autoLogin
        AccountManager.status(if (AccountManager.autoLogin) "Auto-login enabled" else "Auto-login disabled")
        return true
    }

    if (searchClearRect.width > 0f && searchClearRect.contains(mx, my) && searchQuery.isNotEmpty()) {
        searchQuery = ""
        scrollTarget = 0f
        return true
    }
    if (searchBoxRect.contains(mx, my)) {
        searchFocused = true
        cursorTimer = System.currentTimeMillis()
        return true
    }
    searchFocused = false

    val thumb = scrollbarThumb
    if (thumb != null && thumb.copy(x = thumb.x - 5f, width = thumb.width + 10f).contains(mx, my)) {
        draggingScrollbar = true
        dragGrabOffset = my - thumb.y
        return true
    }

    if (listRect.contains(mx, my)) {
        for (row in visibleRows) {
            if (!row.rect.contains(mx, my)) continue
            selectedId = row.account.id
            val now = System.currentTimeMillis()
            val isDouble = (now - lastClickTime < 320L && lastClickedId == row.account.id) || row.quick.contains(mx, my)
            if (isDouble) {
                AccountManager.login(row.account)
                return true
            }
            lastClickTime = now
            lastClickedId = row.account.id
            return true
        }
    }
    return false
}

internal fun AccountManagerScreen.handleMouseReleased(): Boolean {
    draggingScrollbar = false
    backButton.isPressed = false
    for (b in toolbarButtons) b.isPressed = false
    loginButton.isPressed = false
    copyButton.isPressed = false
    removeButton.isPressed = false
    for (b in modalLeft) b.isPressed = false
    for (b in modalRight) b.isPressed = false
    return false
}

internal fun AccountManagerScreen.handleMouseDragged(event: MouseButtonEvent): Boolean {
    if (!draggingScrollbar || scrollMax <= 0f) return false
    val trackTop = listRect.y + 14f
    val trackH = listRect.height - 28f
    val thumbH = (trackH * trackH / (trackH + scrollMax)).coerceIn(28f, trackH)
    val scrollable = trackH - thumbH
    if (scrollable <= 0f) return false
    val frac = ((event.y().toFloat() - dragGrabOffset - trackTop) / scrollable).coerceIn(0f, 1f)
    scrollTarget = frac * scrollMax
    return true
}

internal fun AccountManagerScreen.handleMouseScrolled(scrollY: Double): Boolean {
    if (closeStartMs > 0L || modal != Modal.NONE) return true
    if (scrollMax > 0f) {
        scrollTarget = (scrollTarget - scrollY.toFloat() * 78f).coerceIn(0f, scrollMax)
        return true
    }
    return false
}

// ---------------------------------------------------------------------------------------------
// Keyboard
// ---------------------------------------------------------------------------------------------

internal fun AccountManagerScreen.handleKeyPressed(event: KeyEvent): Boolean {
    val key = event.key
    val ctrl = event.modifiers and GLFW.GLFW_MOD_CONTROL != 0
    if (closeStartMs > 0L) return true

    if (modal != Modal.NONE) {
        when {
            key == GLFW.GLFW_KEY_ESCAPE -> closeModal()
            key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER -> confirmModal()
            key == GLFW.GLFW_KEY_BACKSPACE && modal.takesInput() && modalInput.isNotEmpty() ->
                setModalInput(modalInput.dropLast(1))
            key == GLFW.GLFW_KEY_V && ctrl -> pasteIntoModal()
        }
        return true
    }

    if (key == GLFW.GLFW_KEY_ESCAPE) {
        if (searchFocused) {
            searchFocused = false
        } else {
            requestClose { closeScreen() }
        }
        return true
    }

    if (searchFocused) {
        when {
            key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER -> searchFocused = false
            key == GLFW.GLFW_KEY_BACKSPACE && searchQuery.isNotEmpty() -> {
                searchQuery = searchQuery.dropLast(1)
                scrollTarget = 0f
            }
            key == GLFW.GLFW_KEY_V && ctrl -> {
                val clip = mc.keyboardHandler.clipboard
                if (clip.isNotEmpty()) {
                    searchQuery = (searchQuery + clip.replace("\n", "").replace("\r", "")).take(32)
                    scrollTarget = 0f
                }
            }
            else -> return false
        }
        return true
    }

    if (key == GLFW.GLFW_KEY_F && ctrl) {
        searchFocused = true
        cursorTimer = System.currentTimeMillis()
        return true
    }

    when (key) {
        GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
            val list = visible
            if (list.isEmpty()) return true
            val current = list.indexOfFirst { it.id == selectedId }
            val delta = if (key == GLFW.GLFW_KEY_UP) -1 else 1
            val next = if (current < 0) 0 else (current + delta).coerceIn(0, list.size - 1)
            selectedId = list[next].id
            val rowTop = next * (ROW_H + ROW_GAP)
            val viewport = listRect.height - ROW_PAD * 2f
            if (rowTop < scrollTarget) scrollTarget = rowTop
            else if (rowTop + ROW_H > scrollTarget + viewport) scrollTarget = rowTop + ROW_H - viewport
            scrollTarget = scrollTarget.coerceIn(0f, scrollMax)
            return true
        }
        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
            loginSelected()
            return true
        }
        GLFW.GLFW_KEY_DELETE -> {
            removeSelected()
            return true
        }
    }
    return false
}

internal fun AccountManagerScreen.handleCharTyped(event: CharacterEvent): Boolean {
    val codepoint = event.codepoint
    if (codepoint <= 0) return false
    val char = codepoint.toChar()
    if (char.isISOControl()) return false

    if (modal != Modal.NONE) {
        if (!modal.takesInput() || !modalFocused) return true
        when (modal) {
            Modal.OFFLINE -> if (modalInput.length < 16 && (char.isLetterOrDigit() || char == '_')) setModalInput(modalInput + char)
            Modal.COOKIE -> if (modalInput.length < COOKIE_CAP) setModalInput(modalInput + char)
            else -> if (!char.isWhitespace() && modalInput.length < TOKEN_CAP) setModalInput(modalInput + char)
        }
        return true
    }

    if (searchFocused && searchQuery.length < 32) {
        searchQuery += char
        scrollTarget = 0f
        return true
    }
    return false
}
