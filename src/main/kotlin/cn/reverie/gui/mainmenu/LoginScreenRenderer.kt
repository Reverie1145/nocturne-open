package cn.reverie.gui.mainmenu

import cn.reverie.gui.theme.MenuTheme
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.PI
import kotlin.math.sin

internal fun LoginScreen.renderTransparentAuthLayout(
    mouseX: Float,
    mouseY: Float,
    screenW: Float,
    screenH: Float,
    open: Float
) {
    val marginX = (screenW * 0.065f).coerceIn(48f, 100f)
    val slideX = (1f - open) * -32f

    val shakeOffset = if (shakeAnim.value > 0.001f && !shakeAnim.isFinished) {
        val p = shakeAnim.value
        sin(p * PI * 6.0).toFloat() * (1f - p) * 8.0f
    } else 0f

    val renderX = marginX + slideX + shakeOffset

    val mProg = modeAnim.value
    val confirmExtra = (LoginScreen.FIELD_HEIGHT + LoginScreen.FIELD_GAP) * mProg
    val formW = LoginScreen.FORM_WIDTH
    val formInnerH = 46f + 20f + 28f + 16f + (LoginScreen.FIELD_HEIGHT * 2 + LoginScreen.FIELD_GAP) + confirmExtra + 14f + 18f + 16f + 42f + 20f
    val cardPad = 22f
    val cardW = formW + cardPad * 2f
    val cardH = formInnerH + cardPad * 2f
    val cardX = renderX - cardPad
    val cardY = ((screenH - cardH) * 0.5f).coerceAtLeast(20f)
    val startY = cardY + cardPad

    val nvg = RenderEngine.nanoVG
    nvg.transform.save()
    nvgGlobalAlpha(nvg.handle, open.coerceIn(0f, 1f))

    // Frosted glass card backing the entire form
    MenuTheme.drawGlass(cardX, cardY, cardW, cardH, 16f, screenW, screenH, open, tint = 0.42f)

    // Brand header inside card
    MenuTheme.drawCrescent(renderX + 8f, startY + 12f, 8f, open)
    MenuTheme.heading {
        NanoVGRenderer.drawVerticallyCenteredString("Nocturne", renderX + 24f, startY + 12f, MenuTheme.TEXT.copy(a = open), 16f)
    }

    val badgeLabel = if (mode == LoginScreen.Mode.LOGIN) "Sign in" else "Register"
    val badgeX = renderX + formW - 68f
    val badgeY = startY + 4f
    MenuTheme.drawChip(badgeX, badgeY + 10f, badgeLabel, open, accented = true)

    val subtitle = if (mode == LoginScreen.Mode.LOGIN) "Welcome back. Enter your credentials." else "Create your client account."
    NanoVGRenderer.drawVerticallyCenteredString(subtitle, renderX, startY + 34f, MenuTheme.TEXT_MUTED.copy(a = 0.85f * open), 11.5f)

    var curY = startY + 54f

    // Segmented tab switcher
    val tabH = 30f
    val tabW = (formW - 4f) * 0.5f
    tabLoginRect.x = renderX
    tabLoginRect.y = curY
    tabLoginRect.w = tabW
    tabLoginRect.h = tabH

    tabRegRect.x = renderX + tabW + 4f
    tabRegRect.y = curY
    tabRegRect.w = tabW
    tabRegRect.h = tabH

    tabLoginHover.animateTo(if (tabLoginRect.contains(mouseX, mouseY)) 1f else 0f)
    tabRegHover.animateTo(if (tabRegRect.contains(mouseX, mouseY)) 1f else 0f)

    Draw2D.roundedRect(renderX, curY, formW, tabH, tabH * 0.5f, NanoVGColor(1f, 1f, 1f, 0.04f * open))
    Draw2D.roundedOutline(renderX, curY, formW, tabH, tabH * 0.5f, MenuTheme.HAIRLINE.copy(a = 0.14f * open), 1f)

    val pillX = renderX + 2f + mProg * (tabW + 2f)
    Draw2D.roundedRect(pillX, curY + 2f, tabW - 2f, tabH - 4f, (tabH - 4f) * 0.5f, NanoVGColor(1f, 1f, 1f, 0.12f * open))
    Draw2D.roundedOutline(pillX, curY + 2f, tabW - 2f, tabH - 4f, (tabH - 4f) * 0.5f, MenuTheme.HAIRLINE.copy(a = 0.28f * open), 1f)

    val loginTextColor = if (mode == LoginScreen.Mode.LOGIN) MenuTheme.TEXT else MenuTheme.TEXT_MUTED
    val regTextColor = if (mode == LoginScreen.Mode.REGISTER) MenuTheme.TEXT else MenuTheme.TEXT_MUTED
    MenuTheme.heading {
        NanoVGRenderer.drawCenteredString("Sign In", tabLoginRect.x + tabW * 0.5f, tabLoginRect.y + tabH * 0.5f, loginTextColor.copy(a = open), 12f)
        NanoVGRenderer.drawCenteredString("Sign Up", tabRegRect.x + tabW * 0.5f, tabRegRect.y + tabH * 0.5f, regTextColor.copy(a = open), 12f)
    }

    curY += tabH + 16f

    // Username
    userFieldRect.x = renderX
    userFieldRect.y = curY
    userFieldRect.w = formW
    userFieldRect.h = LoginScreen.FIELD_HEIGHT

    renderTransparentInputField(
        rect = userFieldRect,
        iconType = 0,
        value = username,
        placeholder = "Username",
        focused = usernameFocused,
        focusAnim = userFocusAnim,
        hoverAnim = userHoverAnim,
        masked = false,
        open = open,
        mouseX = mouseX,
        mouseY = mouseY
    )

    curY += LoginScreen.FIELD_HEIGHT + LoginScreen.FIELD_GAP

    // Password
    passFieldRect.x = renderX
    passFieldRect.y = curY
    passFieldRect.w = formW
    passFieldRect.h = LoginScreen.FIELD_HEIGHT
    passEyeRect.x = renderX + formW - 28f
    passEyeRect.y = curY + (LoginScreen.FIELD_HEIGHT - 20f) * 0.5f
    passEyeRect.w = 20f
    passEyeRect.h = 20f

    renderTransparentInputField(
        rect = passFieldRect,
        iconType = 1,
        value = password,
        placeholder = "Password",
        focused = passwordFocused,
        focusAnim = passFocusAnim,
        hoverAnim = passHoverAnim,
        masked = !showPassword,
        open = open,
        mouseX = mouseX,
        mouseY = mouseY,
        eyeRect = passEyeRect,
        eyeVisible = showPassword,
        eyeHoverAnim = eyeHoverAnim
    )

    curY += LoginScreen.FIELD_HEIGHT + LoginScreen.FIELD_GAP

    // Confirm password (register mode)
    if (mProg > 0.01f) {
        confirmFieldRect.x = renderX
        confirmFieldRect.y = curY
        confirmFieldRect.w = formW
        confirmFieldRect.h = LoginScreen.FIELD_HEIGHT * mProg
        confirmEyeRect.x = renderX + formW - 28f
        confirmEyeRect.y = curY + (LoginScreen.FIELD_HEIGHT - 20f) * 0.5f
        confirmEyeRect.w = 20f
        confirmEyeRect.h = 20f

        val nvgHandle = RenderEngine.nanoVG.handle
        nvgScissor(nvgHandle, renderX - 4f, curY - 2f, formW + 8f, (LoginScreen.FIELD_HEIGHT + 4f) * mProg)

        renderTransparentInputField(
            rect = LoginScreen.RectF(renderX, curY, formW, LoginScreen.FIELD_HEIGHT),
            iconType = 2,
            value = confirmPassword,
            placeholder = "Confirm password",
            focused = confirmFocused,
            focusAnim = confirmFocusAnim,
            hoverAnim = confirmHoverAnim,
            masked = !showConfirmPassword,
            open = open * mProg,
            mouseX = mouseX,
            mouseY = mouseY,
            eyeRect = confirmEyeRect,
            eyeVisible = showConfirmPassword,
            eyeHoverAnim = confirmEyeHoverAnim
        )

        nvgResetScissor(nvgHandle)
        curY += (LoginScreen.FIELD_HEIGHT + LoginScreen.FIELD_GAP) * mProg
    } else {
        confirmFieldRect.h = 0f
    }

    // Invite code (register mode)
    if (mProg > 0.01f) {
        inviteFieldRect.x = renderX
        inviteFieldRect.y = curY
        inviteFieldRect.w = formW
        inviteFieldRect.h = LoginScreen.FIELD_HEIGHT * mProg

        val nvgHandle = RenderEngine.nanoVG.handle
        nvgScissor(nvgHandle, renderX - 4f, curY - 2f, formW + 8f, (LoginScreen.FIELD_HEIGHT + 4f) * mProg)

        renderTransparentInputField(
            rect = LoginScreen.RectF(renderX, curY, formW, LoginScreen.FIELD_HEIGHT),
            iconType = 2,
            value = inviteCode,
            placeholder = "Invite code (NOC-XXXX)",
            focused = inviteFocused,
            focusAnim = inviteFocusAnim,
            hoverAnim = inviteHoverAnim,
            masked = false,
            open = open * mProg,
            mouseX = mouseX,
            mouseY = mouseY
        )

        nvgResetScissor(nvgHandle)
        curY += (LoginScreen.FIELD_HEIGHT + LoginScreen.FIELD_GAP) * mProg
    } else {
        inviteFieldRect.h = 0f
    }

    // Remember credentials
    val pillW = 28f
    val pillH = 16f
    rememberRect.x = renderX
    rememberRect.y = curY
    rememberRect.w = formW
    rememberRect.h = pillH

    rememberPill.animateTo(if (remember) 1f else 0f)
    val remVal = rememberPill.value
    val isRemHover = rememberRect.contains(mouseX, mouseY)

    Draw2D.roundedOutline(
        renderX, curY, pillW, pillH, pillH * 0.5f,
        if (remember) MenuTheme.ACCENT.copy(a = 0.55f * open) else MenuTheme.HAIRLINE.copy(a = (if (isRemHover) 0.30f else 0.16f) * open),
        1f
    )
    if (remVal > 0.01f) {
        Draw2D.roundedRect(
            renderX, curY, pillW, pillH, pillH * 0.5f,
            MenuTheme.ACCENT.copy(a = 0.22f * remVal * open)
        )
    }
    val knobX = renderX + 2f + remVal * (pillW - pillH)
    Draw2D.circle(
        knobX + (pillH - 4f) * 0.5f, curY + pillH * 0.5f, (pillH - 4f) * 0.5f,
        if (remember) MenuTheme.ACCENT.copy(a = open) else NanoVGColor(1f, 1f, 1f, 0.45f * open)
    )

    NanoVGRenderer.drawVerticallyCenteredString(
        "Remember credentials",
        renderX + pillW + 10f, curY + pillH * 0.5f,
        if (remember) MenuTheme.TEXT.copy(a = open) else MenuTheme.TEXT_MUTED.copy(a = 0.85f * open),
        11f
    )

    curY += pillH + 18f

    // Action Submit Button: uses MenuTheme.drawButton(PRIMARY)
    val btnH = 42f
    submitRect.x = renderX
    submitRect.y = curY
    submitRect.w = formW
    submitRect.h = btnH

    val isSubmitHover = submitRect.contains(mouseX, mouseY) && !busy
    submitHoverAnim.animateTo(if (isSubmitHover) 1f else 0f)
    submitPressAnim.animateTo(if (busy) 0.5f else 0f)

    if (busy) {
        val busyText = if (mode == LoginScreen.Mode.LOGIN) "Authenticating..." else "Creating account..."
        MenuTheme.drawButton(
            submitRect.x, submitRect.y, submitRect.w, submitRect.h,
            label = busyText,
            style = MenuTheme.ButtonStyle.PRIMARY,
            hover = 0f, press = 0.3f, alpha = open, enabled = false,
            screenW = screenW, screenH = screenH
        )
    } else {
        val label = if (mode == LoginScreen.Mode.LOGIN) "Sign In" else "Sign Up"
        MenuTheme.drawButton(
            submitRect.x, submitRect.y, submitRect.w, submitRect.h,
            label = label,
            style = MenuTheme.ButtonStyle.PRIMARY,
            hover = submitHoverAnim.value,
            press = submitPressAnim.value,
            alpha = open,
            withArrow = true,
            screenW = screenW, screenH = screenH
        )
    }

    curY += btnH + 14f

    // Error / status feedback
    if (errorMessage.isNotEmpty()) {
        Draw2D.roundedRect(renderX, curY, formW, 26f, 8f, MenuTheme.DANGER.copy(a = 0.10f * open))
        Draw2D.roundedOutline(renderX, curY, formW, 26f, 8f, MenuTheme.DANGER.copy(a = 0.35f * open), 1f)
        LoginVectorIcons.drawAlertIcon(renderX + 12f, curY + 13f, 11f, MenuTheme.DANGER)
        NanoVGRenderer.drawVerticallyCenteredString(errorMessage, renderX + 26f, curY + 13f, MenuTheme.DANGER.copy(a = open), 11f)
    } else if (isSuccess) {
        val successC = NanoVGColor(0.40f, 0.85f, 0.65f, 1f)
        LoginVectorIcons.drawCheckIcon(renderX + 12f, curY + 13f, 11f, successC)
        NanoVGRenderer.drawVerticallyCenteredString("Authenticated. Welcome to Nocturne", renderX + 26f, curY + 13f, successC.copy(a = open), 11f)
    } else if (statusText.isNotEmpty() && !busy) {
        NanoVGRenderer.drawCenteredString(statusText, renderX + formW * 0.5f, curY + 12f, MenuTheme.TEXT_MUTED.copy(a = 0.85f * open), 11f)
    }

    // Bottom API status
    val statusY = screenH - 24f
    Draw2D.circle(renderX + 4f, statusY, 3f, MenuTheme.ACCENT.copy(a = 0.85f * open))
    NanoVGRenderer.drawVerticallyCenteredString(
        "API Online  \u00B7  nocturne.reverieapi.asia",
        renderX + 14f, statusY,
        MenuTheme.TEXT_MUTED.copy(a = 0.55f * open),
        11f
    )

    nvg.transform.restore()
    nvgGlobalAlpha(nvg.handle, 1f)
}

internal fun LoginScreen.renderTransparentInputField(
    rect: LoginScreen.RectF,
    iconType: Int,
    value: String,
    placeholder: String,
    focused: Boolean,
    focusAnim: FloatAnimation,
    hoverAnim: FloatAnimation,
    masked: Boolean,
    open: Float,
    mouseX: Float,
    mouseY: Float,
    eyeRect: LoginScreen.RectF? = null,
    eyeVisible: Boolean = false,
    eyeHoverAnim: FloatAnimation? = null
) {
    val isHover = rect.contains(mouseX, mouseY)
    hoverAnim.animateTo(if (isHover) 1f else 0f)
    focusAnim.animateTo(if (focused) 1f else 0f)

    val hVal = hoverAnim.value
    val fVal = focusAnim.value
    val radius = 10f

    // Tinted base
    val bgAlpha = (0.03f + 0.04f * hVal + 0.05f * fVal) * open
    Draw2D.roundedRect(rect.x, rect.y, rect.w, rect.h, radius, NanoVGColor(1f, 0.92f, 0.95f, bgAlpha))

    // Border: accent sakura on focus
    val borderAlpha = (0.12f + 0.14f * hVal + 0.50f * fVal) * open
    val borderColor = if (fVal > 0.05f) {
        MenuTheme.ACCENT.copy(a = borderAlpha)
    } else {
        MenuTheme.HAIRLINE.copy(a = borderAlpha)
    }
    Draw2D.roundedOutline(rect.x, rect.y, rect.w, rect.h, radius, borderColor, 1f)

    val iconColor = NanoVGColor(1f, 0.92f, 0.95f, (0.45f + 0.20f * hVal + 0.35f * fVal) * open)
    val iconCx = rect.x + 18f
    val iconCy = rect.y + rect.h * 0.5f

    when (iconType) {
        0 -> LoginVectorIcons.drawUserIcon(iconCx, iconCy, 14f, iconColor)
        1 -> LoginVectorIcons.drawLockIcon(iconCx, iconCy, 14f, iconColor)
        2 -> LoginVectorIcons.drawShieldIcon(iconCx, iconCy, 14f, iconColor)
    }

    if (eyeRect != null && eyeHoverAnim != null) {
        eyeHoverAnim.animateTo(if (eyeRect.contains(mouseX, mouseY)) 1f else 0f)
        val ehVal = eyeHoverAnim.value
        val eyeColor = NanoVGColor(1f, 0.92f, 0.95f, (0.40f + 0.55f * ehVal) * open)
        LoginVectorIcons.drawEyeIcon(eyeRect.x + eyeRect.w * 0.5f, eyeRect.y + eyeRect.h * 0.5f, 13f, eyeColor, eyeVisible)
    }

    val textStartX = rect.x + 36f
    val textMaxW = rect.w - (if (eyeRect != null) 62f else 42f)
    val textCY = rect.y + rect.h * 0.5f

    if (value.isEmpty() && !focused) {
        NanoVGRenderer.drawVerticallyCenteredString(placeholder, textStartX, textCY, MenuTheme.TEXT_MUTED.copy(a = 0.55f * open), 12f)
    } else {
        val display = if (masked && value.isNotEmpty()) "•".repeat(value.length.coerceAtMost(32)) else value
        val textW = NanoVGRenderer.drawVerticallyCenteredString(display, textStartX, textCY, MenuTheme.TEXT.copy(a = open), 12.5f)

        if (focused) {
            val cursorElapsed = System.currentTimeMillis() - cursorTimer
            val cursorAlpha = ((sin(cursorElapsed / 160.0) + 1.0) * 0.5).toFloat() * open
            if (cursorAlpha > 0.08f) {
                val cursorX = (textStartX + textW + 2f).coerceAtMost(textStartX + textMaxW)
                Draw2D.line(cursorX, textCY - 7f, cursorX, textCY + 7f, MenuTheme.ACCENT.copy(a = cursorAlpha * 0.95f), 1.2f)
            }
        }
    }
}
