package cn.reverie.gui.mainmenu

import cn.reverie.module.misc.YuriGallery
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.art.AnimeArt
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.ui.NvgScreen
import cn.reverie.render.video.VideoBackgroundPlayer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.lwjgl.glfw.GLFW
/**
 * Nocturne Client Authentication Gateway · Zinc Precision v4.0
 * Pure Monochrome & Precision Motion Edition
 * Seamlessly blended into the main menu, fully transparent controls.
 */
class LoginScreen : TitleScreen(false), NvgScreen {

    internal enum class Mode { LOGIN, REGISTER }

    companion object {
        internal val ACCENT = NanoVGColor(0.55f, 0.72f, 0.95f)
        internal val TEXT_PRIMARY = NanoVGColor(1f, 1f, 1f, 0.98f)
        internal val TEXT_SECONDARY = NanoVGColor(0.80f, 0.83f, 0.90f, 0.75f)
        internal val TEXT_MUTED = NanoVGColor(1f, 1f, 1f, 0.38f)
        internal val ERROR_COLOR = NanoVGColor(0.96f, 0.42f, 0.42f)
        internal val SUCCESS_COLOR = NanoVGColor(0.40f, 0.88f, 0.62f)

        internal const val FORM_WIDTH = 224f
        internal const val FIELD_HEIGHT = 36f
        internal const val FIELD_GAP = 9f
    }

    // Entrance & Mode Transitions
    internal val openAnim = FloatAnimation(0f, 1f, 480L, CubicBezier.EASE_OUT)
    internal val modeAnim = FloatAnimation(0f, 0f, 260L, CubicBezier.EASE_OUT)

    // Input Focus & Hover Animations
    internal val userFocusAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    internal val passFocusAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    internal val confirmFocusAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    internal val inviteFocusAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)

    internal val userHoverAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    internal val passHoverAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    internal val confirmHoverAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    internal val inviteHoverAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)
    internal val eyeHoverAnim = FloatAnimation(0f, 0f, 140L, CubicBezier.EASE_OUT)
    internal val confirmEyeHoverAnim = FloatAnimation(0f, 0f, 140L, CubicBezier.EASE_OUT)

    // Button Micro-Interactions & Magnetic Physics
    internal val submitHoverAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    internal val submitPressAnim = FloatAnimation(0f, 0f, 90L, CubicBezier.EASE_OUT)
    internal var submitMagX = 0f
    internal var submitMagY = 0f
    internal var submitLaserPhase = 0f
    internal var rippleX = 0f
    internal var rippleY = 0f
    internal var rippleProgress = 1.0f

    // Segmented Tab Hover & Remember Switch
    internal val rememberPill = FloatAnimation(0f, 0f, 220L, CubicBezier.EASE_OUT)
    internal val tabLoginHover = FloatAnimation(0f, 0f, 140L, CubicBezier.EASE_OUT)
    internal val tabRegHover = FloatAnimation(0f, 0f, 140L, CubicBezier.EASE_OUT)

    // Physical Error Damping Shake
    internal val shakeAnim = FloatAnimation(0f, 0f, 420L, CubicBezier.EASE_OUT)

    // Form State
    internal var mode = Mode.LOGIN
    internal var username = ""
    internal var password = ""
    internal var confirmPassword = ""
    internal var inviteCode = ""

    internal var usernameFocused = true
    internal var passwordFocused = false
    internal var confirmFocused = false
    internal var inviteFocused = false

    internal var showPassword = false
    internal var showConfirmPassword = false

    internal var remember = false
    internal var errorMessage = ""
    internal var statusText = ""
    internal var isSuccess = false

    internal var busy = false
    internal var isClosing = false
    internal var cursorTimer = System.currentTimeMillis()

    // Interactive Bounds
    internal data class RectF(var x: Float = 0f, var y: Float = 0f, var w: Float = 0f, var h: Float = 0f) {
        fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
    }

    internal val tabLoginRect = RectF()
    internal val tabRegRect = RectF()
    internal val userFieldRect = RectF()
    internal val passFieldRect = RectF()
    internal val passEyeRect = RectF()
    internal val confirmFieldRect = RectF()
    internal val inviteFieldRect = RectF()
    internal val confirmEyeRect = RectF()
    internal val rememberRect = RectF()
    internal val submitRect = RectF()

    init {
        // Pre-fill remembered credentials without auto-skipping the screen
        val (savedUser, savedPass, savedRemember) = NocturneAuth.rememberedCredentials()
        username = savedUser
        remember = savedRemember
        rememberPill.setImmediate(if (remember) 1f else 0f)

        if (savedPass.isNotEmpty()) {
            password = savedPass
        }
        if (savedUser.isNotEmpty() && savedPass.isEmpty()) {
            usernameFocused = false
            passwordFocused = true
        }
    }

    override fun init() {
        super.init()
        VideoBackgroundPlayer.init()
        isClosing = false
        openAnim.setImmediate(0f)
        openAnim.animateTo(1f)
        cursorTimer = System.currentTimeMillis()
        rippleProgress = 1.0f
        submitMagX = 0f
        submitMagY = 0f
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun isPauseScreen(): Boolean = false
    override fun shouldCloseOnEsc(): Boolean = false

    override fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val open = openAnim.value
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight

        if (isClosing && (openAnim.isFinished || openAnim.value <= 0.01f)) {
            isClosing = false
            Minecraft.getInstance().gui.setScreen(MainMenuScreen())
            return
        }

        // 0. Base Dark Canvas (Guarantees no black screen under any condition)
        Draw2D.gradientLinear(
            0f, 0f, screenW, screenH, 0f,
            NanoVGColor(0.08f, 0.09f, 0.12f, open),
            NanoVGColor(0.04f, 0.05f, 0.07f, open)
        )

        // 1. Full-screen Video Background & Left Depth Vignette
        VideoBackgroundPlayer.render(screenW, screenH, alpha = open)
        Draw2D.gradientLinear(
            0f, 0f, 440f, screenH, 0f,
            NanoVGColor(0f, 0f, 0f, 0.46f * open),
            NanoVGColor(0f, 0f, 0f, 0f),
            horizontal = true
        )

        // 2. Right-side Character Art (if enabled in YuriGallery)
        if (YuriGallery.enabled && YuriGallery.menuArt.value) {
            val standingId = AnimeArt.dailyPick(AnimeArt.MENU_STANDING_POOL)
            val slideX = (1f - open) * 70f
            val breathing = AnimeArt.idleBob(System.currentTimeMillis())
            val artH = screenH * 0.80f
            val baseY = screenH - 6f
            val anchorX = screenW * 0.80f + slideX

            Draw2D.shadow(
                anchorX - artH * 0.24f, baseY - 10f, artH * 0.48f, 10f, 5f, 9f,
                NanoVGColor(0f, 0f, 0f, 0.35f * open), 1f
            )
            AnimeArt.drawStanding(standingId, anchorX, baseY + breathing * 2.2f, artH, alpha = 0.98f * open)

            Draw2D.gradientLinear(
                screenW - 280f, 0f, 280f, screenH, 0f,
                NanoVGColor(0f, 0f, 0f, 0f),
                NanoVGColor(0f, 0f, 0f, 0.28f * open),
                horizontal = true
            )
        }

        // 3. Top-Right Clock & Date
        MainMenuClock.renderTopRightClock(screenW, screenH, open)

        // 4. Left-aligned Transparent Form
        RenderEngine.withUiFont {
            this.renderTransparentAuthLayout(mouseX, mouseY, screenW, screenH, open)
        }
    }

    private fun triggerShake() {
        shakeAnim.setImmediate(0f)
        shakeAnim.animateTo(1f)
    }

    private fun submit() {
        if (busy) return
        val u = username.trim()
        val p = password

        if (u.isEmpty()) {
            errorMessage = "Username is required"
            triggerShake()
            return
        }
        if (p.isEmpty()) {
            errorMessage = "Password is required"
            triggerShake()
            return
        }

        if (mode == Mode.REGISTER) {
            if (confirmPassword.isEmpty()) {
                errorMessage = "Please confirm your password"
                triggerShake()
                return
            }
            if (p != confirmPassword) {
                errorMessage = "Passwords do not match"
                triggerShake()
                return
            }
            if (u.length < 3) {
                errorMessage = "Username must be at least 3 characters"
                triggerShake()
                return
            }
            if (p.length < 4) {
                errorMessage = "Password must be at least 4 characters"
                triggerShake()
                return
            }
            if (inviteCode.trim().isEmpty()) {
                errorMessage = "Invite code is required"
                triggerShake()
                return
            }
        }

        errorMessage = ""
        busy = true

        if (mode == Mode.LOGIN) {
            NocturneAuth.loginAsync(u, p) { result -> onAuthResult(result, u, p) }
        } else {
            statusText = "Creating account..."
            NocturneAuth.registerAsync(u, p, inviteCode.trim().uppercase()) { result ->
                if (result.ok) {
                    NocturneAuth.loginAsync(u, p) { r2 -> onAuthResult(r2, u, p) }
                } else {
                    busy = false
                    errorMessage = result.error
                    triggerShake()
                }
            }
        }
    }

    private fun onAuthResult(result: NocturneAuth.AuthResult, u: String, p: String) {
        if (result.ok) {
            NocturneAuth.persistCredentials(u, p, remember)
            busy = false
            statusText = ""
            errorMessage = ""
            isSuccess = true
            Thread {
                try {
                    Thread.sleep(320L)
                } catch (ignored: Exception) {}
                isClosing = true
                openAnim.animateTo(0f)
            }.start()
        } else {
            busy = false
            errorMessage = result.error
            triggerShake()
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = ResolutionHelper.toCanvasX(event.x().toFloat())
        val my = ResolutionHelper.toCanvasY(event.y().toFloat())
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true

        if (tabLoginRect.contains(mx, my)) {
            if (mode != Mode.LOGIN) {
                mode = Mode.LOGIN
                modeAnim.animateTo(0f)
                errorMessage = ""
                statusText = ""
            }
            return true
        }
        if (tabRegRect.contains(mx, my)) {
            if (mode != Mode.REGISTER) {
                mode = Mode.REGISTER
                modeAnim.animateTo(1f)
                errorMessage = ""
                statusText = ""
            }
            return true
        }

        val clickUser = userFieldRect.contains(mx, my)
        val clickPass = passFieldRect.contains(mx, my)
        val clickPassEye = passEyeRect.contains(mx, my)
        val clickConfirm = confirmFieldRect.contains(mx, my) && mode == Mode.REGISTER
        val clickConfirmEye = confirmEyeRect.contains(mx, my) && mode == Mode.REGISTER
        val clickInvite = inviteFieldRect.contains(mx, my) && mode == Mode.REGISTER

        if (clickPassEye) {
            showPassword = !showPassword
            return true
        }
        if (clickConfirmEye) {
            showConfirmPassword = !showConfirmPassword
            return true
        }

        usernameFocused = clickUser
        passwordFocused = clickPass
        confirmFocused = clickConfirm
        inviteFocused = clickInvite
        if (inviteFocused) confirmFocused = false

        if (usernameFocused || passwordFocused || confirmFocused || inviteFocused) {
            cursorTimer = System.currentTimeMillis()
        }

        if (rememberRect.contains(mx, my)) {
            remember = !remember
            return true
        }

        if (submitRect.contains(mx, my) && !busy) {
            rippleX = mx - submitRect.x
            rippleY = my - submitRect.y
            rippleProgress = 0f
            submit()
            return true
        }

        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key
        if (key == GLFW.GLFW_KEY_ESCAPE) return true

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!busy) submit()
            return true
        }

        if (key == GLFW.GLFW_KEY_TAB) {
            cursorTimer = System.currentTimeMillis()
            if (mode == Mode.LOGIN) {
                usernameFocused = !usernameFocused
                passwordFocused = !passwordFocused
            } else {
                when {
                    usernameFocused -> {
                        usernameFocused = false
                        passwordFocused = true
                        confirmFocused = false
                    }
                    passwordFocused -> {
                        usernameFocused = false
                        passwordFocused = false
                        confirmFocused = true
                        inviteFocused = false
                    }
                    confirmFocused -> {
                        usernameFocused = false
                        passwordFocused = false
                        confirmFocused = false
                        inviteFocused = true
                    }
                    else -> {
                        usernameFocused = true
                        passwordFocused = false
                        confirmFocused = false
                        inviteFocused = false
                    }
                }
            }
            return true
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (usernameFocused && username.isNotEmpty()) username = username.dropLast(1)
            else if (passwordFocused && password.isNotEmpty()) password = password.dropLast(1)
            else if (confirmFocused && confirmPassword.isNotEmpty()) confirmPassword = confirmPassword.dropLast(1)
            else if (inviteFocused && inviteCode.isNotEmpty()) inviteCode = inviteCode.dropLast(1)
            errorMessage = ""
            return true
        }

        if (key == GLFW.GLFW_KEY_V && event.modifiers and GLFW.GLFW_MOD_CONTROL != 0) {
            val clip = Minecraft.getInstance().keyboardHandler.clipboard
            if (clip.isNotEmpty()) {
                if (usernameFocused && username.length < 16) username = (username + clip).take(16)
                else if (passwordFocused && password.length < 64) password = (password + clip).take(64)
                else if (confirmFocused && confirmPassword.length < 64) confirmPassword = (confirmPassword + clip).take(64)
                else if (inviteFocused && inviteCode.length < 24) inviteCode = (inviteCode + clip).take(24).uppercase()
            }
            return true
        }

        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (event.codepoint <= 0) return true
        val char = event.codepoint.toChar()
        if (char.isISOControl()) return true

        if (usernameFocused) {
            if (username.length < 16 && (char.isLetterOrDigit() || char == '_')) {
                username += char
                errorMessage = ""
            }
            return true
        }
        if (passwordFocused) {
            if (password.length < 64) {
                password += char
                errorMessage = ""
            }
            return true
        }
        if (confirmFocused) {
            if (confirmPassword.length < 64) {
                confirmPassword += char
                errorMessage = ""
            }
            return true
        }
        if (inviteFocused) {
            if (inviteCode.length < 24 && (char.isLetterOrDigit() || char == '-')) {
                inviteCode += char.uppercase()
                errorMessage = ""
            }
            return true
        }
        return true
    }
}
