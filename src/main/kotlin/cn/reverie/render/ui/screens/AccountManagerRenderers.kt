package cn.reverie.render.ui.screens

import cn.reverie.app.account.Account
import cn.reverie.app.account.AccountManager
import cn.reverie.app.account.AccountType
import cn.reverie.gui.mainmenu.MenuFrame
import cn.reverie.gui.mainmenu.MenuChrome
import cn.reverie.gui.mainmenu.MenuFx
import cn.reverie.gui.mainmenu.MenuFx.drawTracked
import cn.reverie.gui.mainmenu.MenuFx.glowFill
import cn.reverie.gui.mainmenu.MenuFx.trackedWidth
import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.mainmenu.MenuFx.withTilt
import cn.reverie.gui.mainmenu.MenuScene
import cn.reverie.gui.mainmenu.cjk
import cn.reverie.gui.mainmenu.drawMenuBackdrop
import cn.reverie.gui.mainmenu.drawSurface
import cn.reverie.gui.mainmenu.head
import cn.reverie.gui.mainmenu.mulA
import cn.reverie.gui.mainmenu.pageHeader
import cn.reverie.gui.mainmenu.renderMenuNav
import cn.reverie.gui.mainmenu.stage
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.gui.vanilla.QUICK_MARGIN
import cn.reverie.gui.vanilla.QUICK_SIZE
import cn.reverie.gui.vanilla.ROW_GAP
import cn.reverie.gui.vanilla.ROW_H
import cn.reverie.gui.vanilla.ROW_PAD
import cn.reverie.gui.vanilla.drawInfoRow
import cn.reverie.gui.vanilla.drawSeparator
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.ui.screens.AccountManagerScreen.Modal
import cn.reverie.render.ui.screens.AccountManagerScreen.Pill
import cn.reverie.render.ui.screens.AccountManagerScreen.RectF
import org.lwjgl.nanovg.NanoVG.nvgRestore
import org.lwjgl.nanovg.NanoVG.nvgResetScissor
import org.lwjgl.nanovg.NanoVG.nvgRoundedRect
import org.lwjgl.nanovg.NanoVG.nvgSave
import org.lwjgl.nanovg.NanoVG.nvgScissor
import org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private val TEXT = MenuTheme.TEXT
private val MUTED = MenuTheme.TEXT_MUTED
private val ACCENT = MenuTheme.ACCENT
private val DANGER = MenuFx.DANGER
private val OK = NanoVGColor(0.45f, 0.88f, 0.68f, 1f)

private const val ENTER_MS = 950f
private const val CLOSE_MS = 260f

private fun typeTint(type: AccountType): NanoVGColor = when (type) {
    AccountType.MICROSOFT -> MenuFx.SAKURA
    AccountType.OFFLINE -> MenuFx.SLATE
    AccountType.SESSION -> MenuFx.PEACH
    AccountType.COOKIE -> MenuFx.VIOLET
}

/** Ban state of an account, as shown in rows and the detail panel. Offline accounts have none. */
internal fun banText(acc: Account): String? {
    if (acc.type == AccountType.OFFLINE) return null
    val until = acc.unban
    if (until < 0L) return "Banned"
    val now = System.currentTimeMillis()
    if (until <= now) return null
    val diff = until - now
    val d = diff / 86_400_000L
    val h = (diff / 3_600_000L) % 24L
    val m = (diff / 60_000L) % 60L
    val s = (diff / 1_000L) % 60L
    val text = buildString {
        if (d > 0) append("${d}d ")
        if (h > 0) append("${h}h ")
        if (m > 0 && d == 0L) append("${m}m ")
        if (d == 0L && h == 0L && s > 0) append("${s}s")
    }.trim()
    return "Banned \u00b7 $text"
}

private fun pillWidth(label: String): Float = head { NanoVGRenderer.getTextWidth(label, 12.5f) } + 40f

/** Pill button using the same [MenuTheme.drawButton] styles as the main menu. */
internal fun AccountManagerScreen.drawPill(
    btn: Pill, x: Float, y: Float, w: Float, h: Float,
    alpha: Float, mx: Float, my: Float, blocked: Boolean, withArrow: Boolean = false
) {
    btn.x = x
    btn.y = y
    btn.w = w
    btn.h = h
    val hover = !blocked && btn.isEnabled && btn.contains(mx, my)
    btn.hoverAnim.animateTo(if (hover) 1f else 0f)
    btn.pressAnim.animateTo(if (btn.isPressed) 1f else 0f)
    val style = when {
        btn.isDanger -> MenuTheme.ButtonStyle.DANGER_GHOST
        btn.isPrimary -> MenuTheme.ButtonStyle.PRIMARY
        else -> MenuTheme.ButtonStyle.GHOST
    }
    MenuTheme.drawButton(
        x, y, w, h,
        label = btn.label, style = style,
        hover = btn.hoverAnim.value, press = btn.pressAnim.value,
        alpha = alpha, enabled = btn.isEnabled,
        withArrow = withArrow, leadingBackArrow = btn === backButton
    )
}

// ---------------------------------------------------------------------------------------------
// Frame
// ---------------------------------------------------------------------------------------------

internal fun AccountManagerScreen.renderAccountManager(mouseX: Float, mouseY: Float) {
    val w = ResolutionHelper.scaledWidth
    val h = ResolutionHelper.scaledHeight
    if (w <= 0f || h <= 0f || RenderEngine.nanoVG.handle == 0L) return

    val now = System.currentTimeMillis()
    if (enterMs == 0L) enterMs = now
    val nowNs = System.nanoTime()
    val dt = if (lastFrameNs == 0L) 1f / 60f else ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    lastFrameNs = nowNs

    // Same scene as the main menu, settled one level behind it.
    MenuScene.step(1f, dt, mouseX, mouseY, w, h)
    MenuChrome.beginFrame()
    visibleRows.clear()

    val f = MenuFrame(w, h)
    val enterT = ((now - enterMs) / ENTER_MS).coerceIn(0f, 1f)
    val closeT = if (closeStartMs > 0L) MenuFx.easeInOutCubic((now - closeStartMs) / CLOSE_MS) else 0f
    val rel = if (closeStartMs > 0L) -closeT else -(1f - MenuFx.easeOutCubic(enterT))

    RenderEngine.withUiFont {
        drawMenuBackdrop(f, 1f, now, MenuScene.scroll, MenuScene.parX, MenuScene.parY)
        renderAccountBody(f, rel, mouseX, mouseY, dt)
        if (inMenu) {
            renderMenuNav(f, mouseX, mouseY, 1f, 1f, "Accounts")
        }
        renderAccountModal(f, mouseX, mouseY, now)
    }

    if (closeStartMs > 0L && closeT >= 1f) {
        val action = pendingClose
        pendingClose = null
        closeStartMs = 0L
        enterMs = now
        action?.invoke()
    }
}

private fun AccountManagerScreen.renderAccountBody(f: MenuFrame, rel: Float, mx: Float, my: Float, dt: Float) {
    val k = f.k
    val tf = RenderEngine.nanoVG.transform
    val blocked = modal != Modal.NONE || closeStartMs > 0L

    val titleCy = f.titleCy
    val subCy = f.subCy

    val accounts = AccountManager.filtered(searchQuery)
    visible = accounts
    if (selectedId != null && selected == null) selectedId = null
    val current = AccountManager.currentAccount

    val statusFresh = AccountManager.statusMessage.isNotEmpty() && System.currentTimeMillis() - AccountManager.statusTime < 6000L
    val sub = if (statusFresh) AccountManager.statusMessage
    else "${AccountManager.accounts.size} accounts  \u00b7  signed in as ${current?.name ?: "nobody"}"
    pageHeader(f, rel, 1f, "", "ACCOUNTS", "Accounts", sub)

    val stBack = stage(rel, 1, f.h)
    if (stBack.vis > 0.004f) {
        tf.save()
        tf.translate(0f, stBack.dy * 0.5f)
        drawPill(backButton, f.w - f.margin - 108f, titleCy - 19f, 108f, 38f, stBack.vis, mx, my, blocked)
        tf.restore()
    }

    // Toolbar: search, sign-in methods and the auto-login switch.
    val toolH = 38f
    val toolY = subCy + 26f
    val stTool = stage(rel, 3, f.h)
    if (stTool.vis > 0.004f) {
        tf.save()
        tf.translate(0f, stTool.dy * 0.5f)
        drawSearch(f.margin, toolY, 290f, toolH, stTool.vis, mx, my)
        var bx = f.margin + 290f + 12f
        for (btn in toolbarButtons) {
            val bw = pillWidth(btn.label)
            drawPill(btn, bx, toolY, bw, toolH, stTool.vis, mx, my, blocked)
            bx += bw + 10f
        }
        drawAutoLogin(f.w - f.margin, toolY + toolH * 0.5f, stTool.vis, dt, mx, my, blocked)
        tf.restore()
    }

    val gap = 24f
    val panelY = toolY + toolH + 20f
    val panelH = (f.h - 78f * k.coerceAtMost(1.2f) - panelY).coerceAtLeast(220f)
    val listW = (f.contentW - gap) * 0.585f
    val detailX = f.margin + listW + gap
    val detailW = f.contentW - listW - gap

    renderAccountList(f, f.margin, panelY, listW, panelH, rel, mx, my, dt, accounts, blocked)
    renderAccountDetail(f, detailX, panelY, detailW, panelH, rel, mx, my, blocked)
}

private fun AccountManagerScreen.drawSearch(x: Float, y: Float, w: Float, h: Float, a: Float, mx: Float, my: Float) {
    searchBoxRect = RectF(x, y, w, h)
    searchFocusAnim.animateTo(if (searchFocused) 1f else 0f)
    val fa = searchFocusAnim.value
    val hovered = modal == Modal.NONE && searchBoxRect.contains(mx, my)
    val r = 11f
    val cy = y + h * 0.5f

    Draw2D.roundedRect(x, y, w, h, r, NanoVGColor(0f, 0f, 0f, (0.24f + 0.10f * fa) * a))
    val outline = white(if (hovered) 0.16f else 0.10f).lerp(ACCENT, 0.75f * fa)
    Draw2D.roundedOutline(x, y, w, h, r, outline.mulA((1f + 0.4f * fa) * a), 1f)
    if (fa > 0.01f) Draw2D.glow(x, y, w, h, r, 14f, ACCENT.mulA(0.14f * fa * a))
    MenuTheme.drawSearchIcon(x + 20f, cy, 13f, white(0.50f + 0.45f * fa).mulA(a))

    val textX = x + 38f
    val blink = searchFocused && ((System.currentTimeMillis() - cursorTimer) / 500) % 2L == 0L
    searchClearRect = RectF(0f, 0f, 0f, 0f)
    if (searchQuery.isEmpty()) {
        NanoVGRenderer.drawVerticallyCenteredString("Search accounts...", textX, cy, MUTED.mulA(0.70f * a), 12.5f)
        if (blink) Draw2D.line(textX, cy - 7f, textX, cy + 7f, ACCENT.mulA(a), 1.3f)
    } else {
        val fitted = NanoVGRenderer.trimWithEllipsis(searchQuery, w - 72f, 12.5f)
        val drawn = NanoVGRenderer.drawVerticallyCenteredString(fitted, textX, cy, TEXT.mulA(a), 12.5f)
        if (blink) {
            val cx = (textX + drawn + 1.5f).coerceAtMost(x + w - 30f)
            Draw2D.line(cx, cy - 7f, cx, cy + 7f, ACCENT.mulA(a), 1.3f)
        }
        searchClearRect = RectF(x + w - 32f, cy - 9f, 24f, 18f)
        MenuTheme.drawClearIcon(x + w - 22f, cy, 11f, white(if (searchClearRect.contains(mx, my)) 0.95f else 0.60f).mulA(a))
    }
}

private fun AccountManagerScreen.drawAutoLogin(rightX: Float, cy: Float, a: Float, dt: Float, mx: Float, my: Float, blocked: Boolean) {
    val label = "AUTO LOGIN"
    val lw = head { trackedWidth(label, 9f, 1.8f) }
    val trackW = 38f
    val trackH = 22f
    val totalW = lw + 12f + trackW
    val x = rightX - totalW
    autoLoginRect = RectF(x - 6f, cy - 16f, totalW + 12f, 32f)

    autoLoginAnim.animateTo(if (AccountManager.autoLogin) 1f else 0f)
    val on = autoLoginAnim.value
    val hover = !blocked && autoLoginRect.contains(mx, my)

    head { drawTracked(label, x, cy, MUTED.mulA((if (hover) 0.95f else 0.75f) * a), 9f, 1.8f) }
    val tx = x + lw + 12f
    val ty = cy - trackH * 0.5f
    Draw2D.roundedRect(tx, ty, trackW, trackH, trackH * 0.5f, white(0.12f).mulA(a))
    if (on > 0.01f) {
        Draw2D.gradientLinear(tx, ty, trackW, trackH, trackH * 0.5f, ACCENT.mulA(0.95f * on * a), MenuTheme.ACCENT_WARM.mulA(0.95f * on * a), horizontal = true)
    }
    Draw2D.roundedOutline(tx, ty, trackW, trackH, trackH * 0.5f, white(0.16f).mulA(a), 1f)
    val kx = tx + 11f + (trackW - 22f) * on
    Draw2D.circle(kx, cy + 1f, 8f, NanoVGColor(0f, 0f, 0f, 0.25f * a))
    Draw2D.circle(kx, cy, 8f, white(0.96f).mulA(a))
}

// ---------------------------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------------------------

private fun AccountManagerScreen.renderAccountList(
    f: MenuFrame, x: Float, y: Float, w: Float, h: Float,
    rel: Float, mx: Float, my: Float, dt: Float, accounts: List<Account>, blocked: Boolean
) {
    listRect = RectF(x, y, w, h)
    val totalH = accounts.size * ROW_H + (accounts.size - 1).coerceAtLeast(0) * ROW_GAP
    scrollMax = (totalH - (h - ROW_PAD * 2f)).coerceAtLeast(0f)
    scrollTarget = scrollTarget.coerceIn(0f, scrollMax)
    scrollPos += (scrollTarget - scrollPos) * (1f - exp(-dt * 14f))
    if (abs(scrollTarget - scrollPos) < 0.08f) scrollPos = scrollTarget

    val st = stage(rel, 4, f.h)
    val a = st.vis
    scrollbarThumb = null
    if (a <= 0.004f) return

    val tf = RenderEngine.nanoVG.transform
    val ctx = RenderEngine.nanoVG.handle
    val current = AccountManager.currentAccount
    val overList = !blocked && listRect.contains(mx, my)

    tf.save()
    tf.translate(0f, st.dy)
    withTilt(x + w * 0.5f, y + h * 0.5f, st.rot, -st.rot * 0.25f, 0.94f + 0.06f * a) {
        drawSurface(x, y, w, h, 20f, a, 0f, 0.46f)

        nvgSave(ctx)
        nvgScissor(ctx, x + 1f, y + 1f, w - 2f, h - 2f)
        if (accounts.isEmpty()) {
            val msg = if (searchQuery.isNotEmpty()) "No matches for \"$searchQuery\"" else "No accounts yet"
            val hint = if (searchQuery.isNotEmpty()) "Try a different search." else "Add one with Microsoft or Offline."
            Draw2D.circleOutline(x + w * 0.5f, y + h * 0.5f - 30f, 26f, white(0.14f).mulA(a), 1f)
            MenuTheme.drawCrescent(x + w * 0.5f, y + h * 0.5f - 30f, 9f, a)
            head { NanoVGRenderer.drawCenteredString(msg, x + w * 0.5f, y + h * 0.5f + 20f, TEXT.mulA(0.92f * a), 16f) }
            NanoVGRenderer.drawCenteredString(hint, x + w * 0.5f, y + h * 0.5f + 44f, MUTED.mulA(0.70f * a), 12f)
        } else {
            val cardW = w - ROW_PAD * 2f - (if (scrollMax > 0f) 6f else 0f)
            val cardX = x + ROW_PAD
            var cy = y + ROW_PAD - scrollPos
            for ((index, acc) in accounts.withIndex()) {
                if (cy + ROW_H >= y && cy <= y + h) {
                    val rect = RectF(cardX, cy, cardW, ROW_H)
                    val quick = RectF(rect.x + rect.width - QUICK_SIZE - QUICK_MARGIN, rect.y + (ROW_H - QUICK_SIZE) * 0.5f, QUICK_SIZE, QUICK_SIZE)
                    visibleRows.add(AccountManagerScreen.RowHit(acc, rect, quick))
                    val rs = stage(rel, 5 + min(index, 6), f.h)
                    renderAccountRow(
                        acc, rect, quick, overList && rect.contains(mx, my), acc.id == selectedId,
                        acc.id == current?.id, rs.vis * a, rs.dy * 0.3f, mx, my, dt
                    )
                }
                cy += ROW_H + ROW_GAP
            }
        }
        nvgResetScissor(ctx)
        nvgRestore(ctx)

        if (scrollMax > 0f) {
            val trackX = x + w - 9f
            val trackTop = y + 14f
            val trackH = h - 28f
            val thumbH = (trackH * trackH / (trackH + scrollMax)).coerceIn(28f, trackH)
            val thumbY = trackTop + (trackH - thumbH) * (scrollPos / scrollMax).coerceIn(0f, 1f)
            val thumb = RectF(trackX, thumbY, 3f, thumbH)
            scrollbarThumb = thumb
            val active = draggingScrollbar || (!blocked && thumb.copy(x = thumb.x - 5f, width = thumb.width + 10f).contains(mx, my))
            Draw2D.roundedRect(trackX, trackTop, 3f, trackH, 1.5f, white(0.04f).mulA(a))
            MenuTheme.drawScrollThumb(trackX, thumbY, 3f, thumbH, active, a)
        }
    }
    tf.restore()
}

private fun AccountManagerScreen.renderAccountRow(
    acc: Account, rect: RectF, quick: RectF,
    hover: Boolean, isSelected: Boolean, isCurrent: Boolean,
    alpha: Float, dy: Float, mx: Float, my: Float, dt: Float
) {
    if (alpha <= 0.004f) return
    val target = if (hover) 1f else 0f
    val cur = cardHover.getOrDefault(acc.id, target)
    val hv = cur + (target - cur) * (1f - exp(-dt * 14f))
    cardHover[acc.id] = hv

    val ctx = RenderEngine.nanoVG.handle
    val tint = typeTint(acc.type)

    nvgSave(ctx)
    nvgGlobalAlpha(ctx, alpha.coerceIn(0f, 1f))
    RenderEngine.nanoVG.transform.translate(0f, dy)

    val vx = rect.x + 4f * hv
    val r = 15f
    val cy = rect.y + rect.height * 0.5f

    Draw2D.roundedRect(vx, rect.y, rect.width, rect.height, r, white(0.035f + 0.040f * hv + (if (isSelected) 0.030f else 0f)))
    if (isSelected) Draw2D.gradientLinear(vx, rect.y, rect.width, rect.height, r, ACCENT.mulA(0.17f), ACCENT.mulA(0.02f), horizontal = true)
    if (hv > 0.01f) {
        glowFill(mx.coerceIn(vx, vx + rect.width), cy, rect.width * 0.42f, white(0.075f * hv)) { c ->
            nvgRoundedRect(c, vx, rect.y, rect.width, rect.height, r)
        }
    }
    Draw2D.roundedOutline(vx, rect.y, rect.width, rect.height, r, if (isSelected) ACCENT.mulA(0.60f) else white(0.07f + 0.12f * hv), 1f)
    if (isSelected) Draw2D.roundedRect(vx + 1f, cy - 15f, 3f, 30f, 1.5f, ACCENT.mulA(0.95f))

    // Avatar tile
    val av = ROW_H - 18f
    val ax = vx + 14f
    val ay = rect.y + 9f
    if (isCurrent) Draw2D.glow(ax, ay, av, av, av * 0.24f, 14f, tint.mulA(0.30f))
    Draw2D.gradientDiagonal(ax, ay, av, av, av * 0.24f, tint.mulA(0.34f), tint.mulA(0.08f))
    Draw2D.roundedOutline(ax, ay, av, av, av * 0.24f, tint.lerp(NanoVGColor.WHITE, 0.4f).mulA(0.35f), 1f)
    HudVectorIcons.drawMoon(ax + (av - 24f) * 0.5f, ay + (av - 24f) * 0.5f, 24f, white(if (isCurrent) 0.98f else 0.80f))

    val textLeft = ax + av + 16f
    val rightReserve = QUICK_SIZE + QUICK_MARGIN * 2f
    var rightEdge = rect.x + rect.width - rightReserve

    if (isCurrent) {
        val pw = 62f
        val px = rightEdge - pw
        val pulse = (sin(System.currentTimeMillis() / 320.0) * 0.15 + 0.85).toFloat()
        Draw2D.roundedRect(px, cy - 11f, pw, 22f, 11f, ACCENT.mulA(0.16f * pulse))
        Draw2D.roundedOutline(px, cy - 11f, pw, 22f, 11f, ACCENT.mulA(0.65f * pulse), 1.2f)
        head { NanoVGRenderer.drawCenteredString("ACTIVE", px + pw * 0.5f, cy, ACCENT.mulA(pulse), 9.5f) }
        rightEdge = px - 12f
    }
    val ban = banText(acc)
    if (ban != null) {
        val bw = NanoVGRenderer.getTextWidth(ban, 11.5f)
        NanoVGRenderer.drawRightVerticallyCenteredString(ban, rightEdge, cy, DANGER.mulA(0.95f), 11.5f)
        rightEdge -= bw + 14f
    }

    val nameMaxW = rightEdge - textLeft - 8f
    cjk {
        NanoVGRenderer.drawVerticallyCenteredString(
            NanoVGRenderer.trimWithEllipsis(acc.name, nameMaxW, 14.5f), textLeft, cy - 11f,
            (if (isSelected || hover) NanoVGColor.WHITE else NanoVGColor(0.90f, 0.88f, 0.92f, 1f)), 14.5f
        )
    }
    Draw2D.circle(textLeft + 3f, cy + 12f, 3f, tint.mulA(0.95f))
    NanoVGRenderer.drawVerticallyCenteredString(
        NanoVGRenderer.trimWithEllipsis("${acc.type.displayName}  \u00b7  ${acc.getLastUsedFormatted()}", nameMaxW - 12f, 11f),
        textLeft + 12f, cy + 12f, MUTED.mulA(0.72f), 11f
    )

    if (hv > 0.02f || isSelected) {
        val qa = if (isSelected) maxOf(hv, 0.55f) else hv
        val qHover = hover && quick.contains(mx, my)
        val qcx = quick.x + 4f * hv + quick.width * 0.5f
        Draw2D.circle(qcx, cy, quick.width * 0.5f, white(0.07f * qa))
        if (qHover || isSelected) {
            Draw2D.circle(qcx, cy, quick.width * 0.5f, ACCENT.lerp(MenuTheme.ACCENT_WARM, 0.35f).mulA((if (qHover) 0.95f else 0.22f) * qa))
        }
        Draw2D.circleOutline(qcx, cy, quick.width * 0.5f, white(0.14f * qa), 1f)
        MenuTheme.drawArrow(qcx - 0.5f + (if (qHover) 1.5f else 0f), cy, (if (qHover) MenuTheme.INK_DEEP else TEXT).mulA(0.95f * qa), if (qHover) 0.6f else 0f)
    }
    nvgRestore(ctx)
}

// ---------------------------------------------------------------------------------------------
// Detail panel
// ---------------------------------------------------------------------------------------------

private fun AccountManagerScreen.renderAccountDetail(
    f: MenuFrame, x: Float, y: Float, w: Float, h: Float,
    rel: Float, mx: Float, my: Float, blocked: Boolean
) {
    val st = stage(rel, 6, f.h)
    val a = st.vis
    if (a <= 0.004f) return

    val acc = selected
    val current = AccountManager.currentAccount
    loginButton.isEnabled = acc != null
    copyButton.isEnabled = acc != null
    removeButton.isEnabled = acc != null
    loginButton.label = if (acc != null && acc.id == current?.id) "Sign in again" else "Log in"

    val tf = RenderEngine.nanoVG.transform
    tf.save()
    tf.translate(0f, st.dy)
    withTilt(x + w * 0.5f, y + h * 0.5f, st.rot, -st.rot * 0.25f, 0.94f + 0.06f * a) {
        val r = 20f
        val pad = 28f
        drawSurface(x, y, w, h, r, a, 0f, 0.50f)

        val px = MenuScene.parX
        val py = MenuScene.parY
        val tint = if (acc != null) typeTint(acc.type) else ACCENT
        glowFill(x + w * 0.84f - px * 18f, y + h * 0.12f - py * 12f, w * 0.72f, tint.mulA(0.20f * a)) { c ->
            nvgRoundedRect(c, x, y, w, h, r)
        }
        glowFill(x, y + h, w * 0.6f, MenuTheme.ACCENT_WARM.mulA(0.08f * a)) { c -> nvgRoundedRect(c, x, y, w, h, r) }

        if (acc != null) {
            val size = 76f
            val ix = x + pad - px * 3f
            val iy = y + pad - py * 2f
            val ir = size * 0.24f
            Draw2D.glow(ix, iy, size, size, ir, 22f, tint.mulA(0.28f * a))
            Draw2D.gradientDiagonal(ix, iy, size, size, ir, tint.mulA(0.40f * a), tint.mulA(0.10f * a))
            Draw2D.roundedOutline(ix, iy, size, size, ir, tint.lerp(NanoVGColor.WHITE, 0.4f).mulA(0.4f * a), 1f)
            HudVectorIcons.drawMoon(ix + (size - 34f) * 0.5f, iy + (size - 34f) * 0.5f, 34f, white(0.97f).mulA(a))

            val tx = ix + size + 18f
            val textW = x + w - pad - tx
            head { drawTracked("ACCOUNT  \u00b7  ${acc.type.tag}", tx, iy + 10f, tint.lerp(NanoVGColor.WHITE, 0.25f).mulA(0.95f * a), 9f, 2.0f) }
            cjk {
                NanoVGRenderer.drawVerticallyCenteredString(
                    NanoVGRenderer.trimWithEllipsis(acc.name, textW, 24f), tx, iy + 38f, TEXT.mulA(a), 24f
                )
                NanoVGRenderer.drawVerticallyCenteredString(
                    NanoVGRenderer.trimWithEllipsis("${acc.getShortUuid()}  \u00b7  Last used ${acc.getLastUsedFormatted()}", textW, 12.5f),
                    tx, iy + 66f, MUTED.mulA(0.78f * a), 12.5f
                )
            }

            var ry = iy + size + 30f
            ry = drawSeparator(x + pad, x + w - pad, ry - 14f, a) + 14f
            ry = drawInfoRow(x + pad, x + w - pad, ry, "Type", acc.type.displayName, null, a)
            val isCurrent = acc.id == current?.id
            ry = drawInfoRow(x + pad, x + w - pad, ry, "Session", if (isCurrent) "Active" else "Idle", if (isCurrent) OK else null, a)
            val ban = banText(acc)
            val statusText = when {
                acc.type == AccountType.OFFLINE -> "Offline mode"
                ban != null -> ban
                else -> "Clear"
            }
            drawInfoRow(x + pad, x + w - pad, ry, "Status", statusText, if (ban != null) DANGER else if (acc.type == AccountType.OFFLINE) null else OK, a)
        } else {
            val cx = x + w * 0.5f
            val cy = y + h * 0.5f - 62f
            Draw2D.circleOutline(cx, cy, 42f, white(0.07f).mulA(a), 1f)
            Draw2D.circleOutline(cx, cy, 30f, white(0.12f).mulA(a), 1f)
            MenuTheme.drawCrescent(cx, cy, 11f, a)
            head { NanoVGRenderer.drawCenteredString("Select an account", cx, cy + 66f, TEXT.mulA(0.92f * a), 17f) }
            NanoVGRenderer.drawCenteredString("Details and actions appear here.", cx, cy + 90f, MUTED.mulA(0.70f * a), 12f)
        }

        val secH = 40f
        val priH = 52f
        val secY = y + h - pad - secH
        val priY = secY - 12f - priH
        drawPill(loginButton, x + pad, priY, w - pad * 2f, priH, a, mx, my, blocked, withArrow = true)
        val g = 10f
        val bw = (w - pad * 2f - g) / 2f
        drawPill(copyButton, x + pad, secY, bw, secH, a, mx, my, blocked)
        drawPill(removeButton, x + pad + bw + g, secY, bw, secH, a, mx, my, blocked)
    }
    tf.restore()
}

// ---------------------------------------------------------------------------------------------
// Modal
// ---------------------------------------------------------------------------------------------

private fun AccountManagerScreen.renderAccountModal(f: MenuFrame, mx: Float, my: Float, now: Long) {
    modalAnim.animateTo(if (modal != Modal.NONE) 1f else 0f)
    val a = modalAnim.value
    if (a <= 0.004f && modal == Modal.NONE) {
        modalCardRect = RectF(0f, 0f, 0f, 0f)
        return
    }
    val type = if (modal != Modal.NONE) modal else lastModal
    val w = f.w
    val h = f.h
    val tf = RenderEngine.nanoVG.transform

    Draw2D.rect(0f, 0f, w, h, NanoVGColor(0f, 0f, 0f, 0.52f * a))

    val cw = min(560f, w - 48f)
    val ch = if (type == Modal.MS_DEVICE) 316f else 296f
    val cx = (w - cw) * 0.5f
    val cy = (h - ch) * 0.5f
    modalCardRect = if (modal != Modal.NONE) RectF(cx, cy, cw, ch) else RectF(0f, 0f, 0f, 0f)

    tf.save()
    tf.translate(w * 0.5f, h * 0.5f + (1f - a) * 14f)
    tf.scale(0.95f + 0.05f * a, 0.95f + 0.05f * a)
    tf.translate(-w * 0.5f, -h * 0.5f)

    val r = 22f
    val pad = 32f
    drawSurface(cx, cy, cw, ch, r, a, 0f, 0.84f)
    glowFill(cx + cw, cy, cw * 0.7f, ACCENT.mulA(0.16f * a)) { c -> nvgRoundedRect(c, cx, cy, cw, ch, r) }

    val (title, lines) = when (type) {
        Modal.OFFLINE -> "Offline account" to listOf("Pick any name (3-16 letters, digits or _).", "No network request is made.")
        Modal.TOKEN -> "Token login" to listOf("Paste a Microsoft refresh token or a Minecraft access token.", "A refresh token is exchanged for a fresh session.")
        Modal.SESSION -> "Session login" to listOf("Paste ign:uuid:token, or just a Minecraft access token.", "The session is used as it is.")
        Modal.COOKIE -> "Cookie login" to listOf("Load an exported cookies file (cookies.txt, JSON or a Cookie header).", "The Microsoft cookies are replayed to sign in without a browser.")
        Modal.MS_BROWSER -> "Microsoft sign-in" to listOf("Sign in with your Microsoft account in the browser.", "This window continues on its own once you are done.")
        Modal.MS_DEVICE -> "Device code" to listOf("Open the link, enter the code below and approve the sign-in.")
        Modal.NONE -> "" to emptyList()
    }
    head { drawTracked("ACCOUNTS", cx + pad, cy + 30f, ACCENT.mulA(0.9f * a), 9f, 2.0f) }
    head { NanoVGRenderer.drawVerticallyCenteredString(title, cx + pad, cy + 56f, TEXT.mulA(a), 22f) }
    var ty = cy + 88f
    for (line in lines) {
        NanoVGRenderer.drawVerticallyCenteredString(
            NanoVGRenderer.trimWithEllipsis(line, cw - pad * 2f, 12.5f), cx + pad, ty, MUTED.mulA(0.80f * a), 12.5f
        )
        ty += 19f
    }
    val contentTop = ty + 8f
    val contentW = cw - pad * 2f

    when (type) {
        Modal.OFFLINE, Modal.TOKEN, Modal.SESSION, Modal.COOKIE -> {
            val rect = RectF(cx + pad, contentTop, contentW, 46f)
            if (modal != Modal.NONE) modalInputRect = rect
            val focused = modalFocused && modal != Modal.NONE
            Draw2D.roundedRect(rect.x, rect.y, rect.width, rect.height, 12f, NanoVGColor(0f, 0f, 0f, 0.32f * a))
            Draw2D.roundedOutline(
                rect.x, rect.y, rect.width, rect.height, 12f,
                (if (focused) ACCENT.mulA(0.65f) else white(0.12f)).mulA(a), 1f
            )
            if (focused) Draw2D.glow(rect.x, rect.y, rect.width, rect.height, 12f, 12f, ACCENT.mulA(0.12f * a))

            val hint = when (type) {
                Modal.OFFLINE -> "Username"
                Modal.TOKEN -> "Paste a token"
                Modal.SESSION -> "ign:uuid:token"
                else -> "Load a cookie file or paste cookies"
            }
            val cyIn = rect.y + rect.height * 0.5f
            val blink = focused && ((now - cursorTimer) / 500) % 2L == 0L
            if (modalInput.isEmpty()) {
                NanoVGRenderer.drawVerticallyCenteredString(hint, rect.x + 18f, cyIn, MUTED.mulA(0.6f * a), 13.5f)
                if (blink) Draw2D.line(rect.x + 18f, cyIn - 8f, rect.x + 18f, cyIn + 8f, ACCENT.mulA(a), 1.3f)
            } else {
                val shown = NanoVGRenderer.trimWithEllipsis(modalSummary, rect.width - 36f, 13.5f)
                val drawn = NanoVGRenderer.drawVerticallyCenteredString(shown, rect.x + 18f, cyIn, TEXT.mulA(a), 13.5f)
                if (blink && type == Modal.OFFLINE) {
                    Draw2D.line(rect.x + 18f + drawn + 1.5f, cyIn - 8f, rect.x + 18f + drawn + 1.5f, cyIn + 8f, ACCENT.mulA(a), 1.3f)
                }
            }
            drawModalStatus(cx + pad, rect.y + rect.height + 24f, contentW, a, now)
        }
        Modal.MS_BROWSER -> {
            val boxH = 72f
            Draw2D.roundedRect(cx + pad, contentTop, contentW, boxH, 14f, NanoVGColor(0f, 0f, 0f, 0.28f * a))
            Draw2D.roundedOutline(cx + pad, contentTop, contentW, boxH, 14f, white(0.10f).mulA(a), 1f)
            val link = browserSession?.link ?: ""
            head { drawTracked(if (linkCopied) "LINK COPIED" else "LOGIN LINK", cx + pad + 18f, contentTop + 22f, MUTED.mulA(0.75f * a), 9f, 1.8f) }
            NanoVGRenderer.drawVerticallyCenteredString(
                NanoVGRenderer.trimWithEllipsis(link, contentW - 36f, 12f), cx + pad + 18f, contentTop + 46f, TEXT.mulA(0.85f * a), 12f
            )
            drawModalStatus(cx + pad, contentTop + boxH + 24f, contentW, a, now)
        }
        Modal.MS_DEVICE -> {
            val boxH = 80f
            Draw2D.roundedRect(cx + pad, contentTop, contentW, boxH, 14f, NanoVGColor(0f, 0f, 0f, 0.28f * a))
            Draw2D.roundedOutline(cx + pad, contentTop, contentW, boxH, 14f, white(0.10f).mulA(a), 1f)
            head {
                val tw = trackedWidth(deviceCode, 34f, 6f)
                drawTracked(deviceCode, cx + cw * 0.5f - tw * 0.5f, contentTop + boxH * 0.5f, TEXT.mulA(a), 34f, 6f)
            }
            drawModalStatus(cx + pad, contentTop + boxH + 24f, contentW, a, now)
        }
        Modal.NONE -> {}
    }

    // Footer buttons: extras on the left, confirm / cancel on the right.
    val bh = 40f
    val by = cy + ch - 28f - bh
    val blocked = modal == Modal.NONE || a < 0.6f
    var lx = cx + pad
    for (p in modalLeft) {
        val bw = pillWidth(p.label)
        p.isEnabled = !modalBusy || !p.isPrimary
        drawPill(p, lx, by, bw, bh, a, mx, my, blocked)
        lx += bw + 10f
    }
    var rx = cx + cw - pad
    for (p in modalRight.asReversed()) {
        val bw = pillWidth(p.label)
        p.isEnabled = !modalBusy || !p.isPrimary
        rx -= bw
        drawPill(p, rx, by, bw, bh, a, mx, my, blocked)
        rx -= 10f
    }
    tf.restore()
}

/** Error (red) or progress text with animated dots while a request is running. */
private fun AccountManagerScreen.drawModalStatus(x: Float, cy: Float, maxW: Float, a: Float, now: Long) {
    if (modalError.isNotEmpty()) {
        NanoVGRenderer.drawVerticallyCenteredString(
            NanoVGRenderer.trimWithEllipsis(modalError, maxW, 12.5f), x, cy, DANGER.mulA(0.95f * a), 12.5f
        )
        return
    }
    if (modalStatus.isEmpty()) return
    val text = NanoVGRenderer.trimWithEllipsis(modalStatus, maxW - 40f, 12.5f)
    val tw = NanoVGRenderer.drawVerticallyCenteredString(text, x, cy, MUTED.mulA(0.85f * a), 12.5f)
    if (modalBusy) {
        for (i in 0 until 3) {
            val phase = ((now / 380.0) + i * 0.33) % 1.0
            val lift = (sin(phase * Math.PI * 2.0)).toFloat() * 2.5f
            Draw2D.circle(x + tw + 12f + i * 7f, cy + lift, 1.8f, ACCENT.mulA((0.5f + 0.4f * (lift / 2.5f + 1f) * 0.5f) * a))
        }
    }
}
