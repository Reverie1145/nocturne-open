package cn.reverie.gui.clickgui.irc
import cn.reverie.module.Category
import cn.reverie.nocturne.IrcManager
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.window.drawScrollBar
import cn.reverie.gui.clickgui.window.WindowClickGuiScreen

internal fun WindowClickGuiScreen.ircRowY(): Float =
    WindowClickGuiScreen.SIDEBAR_Y + (Category.entries.size + 2) * WindowClickGuiScreen.SIDEBAR_ROW_STEP

private fun ircRowHeight(msg: IrcManager.IrcMessage, chatW: Float): Float = when (msg.kind) {
    IrcManager.MsgKind.CHAT, IrcManager.MsgKind.NOTICE -> {
        if (IrcImageCache.imageUrl(msg.text) != null) 22f + 13f + IrcImageCache.previewHeight() + 7f
        else 22f + IrcChatModel.wrap(msg.text, chatW - 24f, 9.5f).totalHeight + 12f
    }
    else -> 24f
}

internal fun WindowClickGuiScreen.handleIrcClick(mx: Float, my: Float, button: Int, doubleClick: Boolean = false): Boolean {
    val onlineMembers = IrcManager.channelMembers()
    val targets = listOf<String?>(null) + (onlineMembers + IrcManager.privateContacts()).distinct().sorted()
    val viewportH = WindowClickGuiScreen.CONTENT_BOTTOM - WindowClickGuiScreen.CONTENT_TOP

    // Middle list clicks (Select Channel / DM)
    if (button == 0 && inside(mx, my, WindowClickGuiScreen.LIST_X, WindowClickGuiScreen.CONTENT_TOP, WindowClickGuiScreen.LIST_W, viewportH)) {
        for ((index, nick) in targets.withIndex()) {
            val y = WindowClickGuiScreen.CONTENT_TOP + index * WindowClickGuiScreen.ROW_STEP - ircMemberScroll
            if (inside(mx, my, WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H)) {
                ircTarget = nick
                ircPinned = true
                ircInputFocused = false
                return true
            }
        }
        return true
    }

    // Right details clicks (Chat view)
    val cx = WindowClickGuiScreen.DETAIL_X
    val cw = WindowClickGuiScreen.DETAIL_W
    val inputH = 28f
    val inputY = WindowClickGuiScreen.CONTENT_BOTTOM - inputH - 6f
    val inputX = cx + 12f
    val inputW = cw - 24f

    // Inbox shares toggle
    val shareX = cx + cw - 99f
    if (button == 0 && inside(mx, my, shareX, 83f, 82f, 18f)) {
        ircOffersOpen = !ircOffersOpen
        return true
    }
    if (ircOffersOpen && handleIrcOffersClick(mx, my, button)) return true

    // Input box click
    if (button == 0 && inside(mx, my, inputX, inputY, inputW, inputH)) {
        ircInputFocused = true
        ircCursorTimer = System.currentTimeMillis()
        val sendSize = 20f
        val sendX = inputX + inputW - sendSize - 7f
        if (inside(mx, my, sendX, inputY + 4f, sendSize, sendSize)) {
            sendIrcMessage()
        }
        return true
    }

    // Message list image click
    val msgTop = 155f
    val msgBottom = inputY - 8f
    val chatW = cw - 24f
    if (button == 0 && inside(mx, my, cx + 12f, msgTop, chatW, msgBottom - msgTop)) {
        val target = ircTarget
        val messages = IrcManager.messagesFor(target ?: IrcManager.channel, direct = target != null)
        var rowY = msgTop - ircScroll
        for (msg in messages) {
            val imageUrl = IrcImageCache.imageUrl(msg.text)
            val rowHeight = ircRowHeight(msg, chatW)
            if (imageUrl != null && inside(mx, my, cx + 12f, rowY + 35f, chatW, IrcImageCache.previewHeight())) {
                val now = System.currentTimeMillis()
                if (doubleClick || (lastIrcImageClickUrl == imageUrl && now - lastIrcImageClickAt < 450L)) {
                    IrcImageViewerScreen.open(imageUrl)
                    lastIrcImageClickAt = 0L
                    lastIrcImageClickUrl = null
                } else {
                    lastIrcImageClickAt = now
                    lastIrcImageClickUrl = imageUrl
                }
                return true
            }
            rowY += rowHeight + 6f
        }
        return true
    }

    ircInputFocused = false
    return false
}

internal fun WindowClickGuiScreen.renderIrcView(mx: Float, my: Float, alpha: Float) {
    val onlineMembers = IrcManager.channelMembers()
    val targets = listOf<String?>(null) + (onlineMembers + IrcManager.privateContacts()).distinct().sorted()

    // --- Middle List: Channels & Contacts ---
    NanoVGRenderer.drawString("CHANNELS & DMS", WindowClickGuiScreen.LIST_X, 87f, NanoVGColor(0.88f, 0.89f, 0.92f, alpha), 10f)
    NanoVGRenderer.drawRightVerticallyCenteredString("${onlineMembers.size} ONLINE", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W, 89f,
        NanoVGColor(0.48f, 0.49f, 0.53f, alpha), 8f)

    val viewportH = WindowClickGuiScreen.CONTENT_BOTTOM - WindowClickGuiScreen.CONTENT_TOP
    val contentH = targets.size * WindowClickGuiScreen.ROW_STEP - 6f
    val maxScroll = (contentH - viewportH).coerceAtLeast(0f)
    ircMemberScrollTarget = ircMemberScrollTarget.coerceIn(0f, maxScroll)
    ircMemberScroll += (ircMemberScrollTarget - ircMemberScroll) * 0.30f
    if (kotlin.math.abs(ircMemberScrollTarget - ircMemberScroll) < 0.08f) ircMemberScroll = ircMemberScrollTarget

    NanoVGRenderer.withScissor(WindowClickGuiScreen.LIST_X, WindowClickGuiScreen.CONTENT_TOP, WindowClickGuiScreen.LIST_W, viewportH) {
        for ((index, nick) in targets.withIndex()) {
            val y = WindowClickGuiScreen.CONTENT_TOP + index * WindowClickGuiScreen.ROW_STEP - ircMemberScroll
            if (y + WindowClickGuiScreen.ROW_H < WindowClickGuiScreen.CONTENT_TOP || y > WindowClickGuiScreen.CONTENT_BOTTOM) continue

            val hovered = inside(mx, my, WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H)
            val selected = if (nick == null) ircTarget == null else ircTarget.equals(nick, ignoreCase = true)
            val isChannel = nick == null

            val rowBg = NanoVGColor(1f, 1f, 1f,
                (0.035f + (if (hovered) 0.035f else 0f) + (if (selected) 0.055f else 0f)) * alpha)
            NanoVGRenderer.drawRoundedRect(WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H, 7f, rowBg)
            NanoVGRenderer.drawRoundedOutline(WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H, 7f,
                NanoVGColor(1f, 1f, 1f, (0.055f + (if (selected) 0.15f else 0f)) * alpha), 0.8f)

            if (isChannel) {
                NanoVGRenderer.drawString("#", WindowClickGuiScreen.LIST_X + 13f, y + 17f,
                    if (selected) NanoVGColor.WHITE else NanoVGColor(0.65f, 0.75f, 0.95f, alpha), 12f)
                val label = "#${IrcManager.channel}"
                NanoVGRenderer.drawMarqueeString(label, WindowClickGuiScreen.LIST_X + 26f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                    150f, hovered, NanoVGColor(0.78f, 0.79f, 0.82f, alpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), if (selected) 1f else 0f), 10.5f)
                NanoVGRenderer.drawRightVerticallyCenteredString("CHANNEL", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 12f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                    NanoVGColor(0.50f, 0.53f, 0.60f, alpha), 7.5f)
            } else {
                val isOnline = onlineMembers.any { it.equals(nick, ignoreCase = true) }
                val dotColor = if (isOnline) IrcChatModel.nickColor(nick) else NanoVGColor(0.46f, 0.47f, 0.51f, 1f)
                NanoVGRenderer.drawCircle(WindowClickGuiScreen.LIST_X + 16f, y + WindowClickGuiScreen.ROW_H * 0.5f, 3.5f, dotColor.withAlpha(alpha))

                NanoVGRenderer.drawMarqueeString("@$nick", WindowClickGuiScreen.LIST_X + 26f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                    150f, hovered, NanoVGColor(0.78f, 0.79f, 0.82f, alpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), if (selected) 1f else 0f), 10.5f)
                NanoVGRenderer.drawRightVerticallyCenteredString("DM", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 12f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                    NanoVGColor(0.50f, 0.53f, 0.60f, alpha), 7.5f)
            }
        }
    }
    drawScrollBar(WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 2f, WindowClickGuiScreen.CONTENT_TOP, viewportH, contentH, ircMemberScroll, alpha)

    // --- Right Details: Chat Conversation ---
    val cx = WindowClickGuiScreen.DETAIL_X
    val cy = 77f
    val cw = WindowClickGuiScreen.DETAIL_W
    val ch = 393f

    NanoVGRenderer.drawRoundedRect(cx, cy, cw, ch, 9f, NanoVGColor(1f, 1f, 1f, 0.025f * alpha))
    NanoVGRenderer.drawRoundedOutline(cx, cy, cw, ch, 9f, NanoVGColor(1f, 1f, 1f, 0.065f * alpha), 0.8f)

    val target = ircTarget
    val titleText = if (target == null) "#${IrcManager.channel}" else "@$target"
    val headerHovered = inside(mx, my, cx, 84f, cw, 40f)

    NanoVGRenderer.drawMarqueeString(titleText, cx + 17f, 103f, 220f, headerHovered,
        NanoVGColor(0.96f, 0.97f, 0.98f, alpha), 16f)

    val offers = cn.reverie.nocturne.IrcShareManager.pendingOffers()
    val shareX = cx + cw - 99f
    val shareHover = inside(mx, my, shareX, 83f, 82f, 18f)
    NanoVGRenderer.drawRoundedRect(shareX, 83f, 82f, 18f, 4f,
        NanoVGColor(1f, 1f, 1f, (if (shareHover || ircOffersOpen) 0.12f else 0.05f) * alpha))
    NanoVGRenderer.drawCenteredString("Inbox ${offers.size}", shareX + 41f, 92f, NanoVGColor(0.82f, 0.84f, 0.88f, alpha), 7.5f)

    val statusSubtitle = if (target == null) "IRC Public Channel · ${IrcChatModel.statusText()}" else "Direct Message with @$target"
    NanoVGRenderer.drawString(statusSubtitle, cx + 17f, 126f, NanoVGColor(0.59f, 0.60f, 0.64f, alpha), 9f)

    Draw2D.line(cx + 17f, 151f, cx + cw - 17f, 151f, NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 1f)

    // Messages Area
    val inputH = 28f
    val inputY = WindowClickGuiScreen.CONTENT_BOTTOM - inputH - 6f
    val inputX = cx + 12f
    val inputW = cw - 24f
    val msgTop = 155f
    val msgBottom = inputY - 8f
    val chatW = cw - 24f
    val chatX = cx + 12f
    val chatViewportH = msgBottom - msgTop

    val messages = IrcManager.messagesFor(target ?: IrcManager.channel, direct = target != null)
    var totalMsgH = 0f
    for (msg in messages) totalMsgH += ircRowHeight(msg, chatW) + 6f
    val maxMsgScroll = (totalMsgH - chatViewportH).coerceAtLeast(0f)
    if (ircPinned) ircScrollTarget = maxMsgScroll
    ircScrollTarget = ircScrollTarget.coerceIn(0f, maxMsgScroll)
    ircScroll += (ircScrollTarget - ircScroll) * 0.30f
    if (kotlin.math.abs(ircScrollTarget - ircScroll) < 0.08f) ircScroll = ircScrollTarget
    ircPinned = maxMsgScroll - ircScroll < 8f

    NanoVGRenderer.withScissor(chatX, msgTop, chatW, chatViewportH) {
        var rowY = msgTop - ircScroll
        for (msg in messages) {
            val h = ircRowHeight(msg, chatW)
            if (rowY + h >= msgTop - 8f && rowY <= msgBottom + 8f) {
                renderIrcMessageRow(msg, chatX, rowY, chatW, alpha)
            }
            rowY += h + 6f
        }
    }
    drawScrollBar(cx + cw - 6f, msgTop, chatViewportH, totalMsgH, ircScroll, alpha)

    // Bottom Input Box
    renderIrcInput(inputX, inputY, inputW, inputH, mx, my, alpha)

    // Incoming shares popup
    if (ircOffersOpen) {
        renderIrcOffersOverlay(cx, cw, alpha, mx, my)
    }
}

private fun WindowClickGuiScreen.renderIrcOffersOverlay(cx: Float, cw: Float, alpha: Float, mx: Float, my: Float) {
    val top = 155f
    val pAlpha = HUDModule.bgOpacity.getFloat() * alpha
    val pBg = NanoVGColor(0.047f, 0.051f, 0.067f, pAlpha)
    if (HUDModule.widgetShadow.value) {
        Draw2D.shadow(cx + 8f, top, cw - 16f, 260f, 6f, 12f, NanoVGColor(0f, 0f, 0f, 0.35f * HUDModule.shadowStrength.getFloat() * alpha))
    }
    try {
        RenderEngine.backdropBlurRenderer.renderBackdrop(
            cx + 8f, top, cw - 16f, 260f,
            radius = 6f,
            tintColor = pBg,
            blurRadius = HUDModule.blurRadius.getFloat(),
            enableBlur = HUDModule.blur.value,
            backdropOpacity = pAlpha
        )
    } catch (_: Throwable) {
        NanoVGRenderer.drawRoundedRect(cx + 8f, top, cw - 16f, 260f, 6f, pBg)
    }
    NanoVGRenderer.drawRoundedOutline(cx + 8f, top, cw - 16f, 260f, 6f, NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.8f)
    NanoVGRenderer.drawString("INCOMING SHARES", cx + 17f, top + 10f, NanoVGColor(0.88f, 0.76f, 0.55f, alpha), 8.5f)

    val offers = cn.reverie.nocturne.IrcShareManager.pendingOffers()
    val visibleOffers = offers.take(5)
    if (visibleOffers.isEmpty()) {
        NanoVGRenderer.drawCenteredString("No pending items", cx + cw * 0.5f, top + 100f, NanoVGColor(0.58f, 0.61f, 0.68f, alpha), 9f)
    } else {
        visibleOffers.forEachIndexed { index, offer ->
            val ry = top + 26f + index * 44f
            val kind = when (offer.kind) { "config" -> "Config"; "module" -> "Module"; else -> "Music" }
            NanoVGRenderer.drawRoundedRect(cx + 14f, ry, cw - 28f, 38f, 5f, NanoVGColor(1f, 1f, 1f, 0.035f * alpha))
            NanoVGRenderer.drawVerticallyCenteredString("${offer.sender} · $kind", cx + 22f, ry + 12f, NanoVGColor(0.85f, 0.87f, 0.91f, alpha), 8.5f)
            Draw2D.roundedRect(cx + cw - 132f, ry + 10f, 52f, 18f, 3f, NanoVGColor(0.25f, 0.58f, 0.39f, 0.75f * alpha))
            NanoVGRenderer.drawCenteredString("Accept", cx + cw - 106f, ry + 19f, NanoVGColor.WHITE.withAlpha(alpha), 7.5f)
            Draw2D.roundedRect(cx + cw - 72f, ry + 10f, 48f, 18f, 3f, NanoVGColor(0.48f, 0.25f, 0.27f, 0.75f * alpha))
            NanoVGRenderer.drawCenteredString("Reject", cx + cw - 48f, ry + 19f, NanoVGColor.WHITE.withAlpha(alpha), 7.5f)
        }
    }
}

private fun WindowClickGuiScreen.renderIrcMessageRow(msg: IrcManager.IrcMessage, x: Float, y: Float, w: Float, alpha: Float) {
    val time = IrcChatModel.timeText(msg.time)
    when (msg.kind) {
        IrcManager.MsgKind.CHAT, IrcManager.MsgKind.NOTICE -> {
            val h = ircRowHeight(msg, w)
            val isOwn = msg.sender.equals(NocturneAuth.username, ignoreCase = true)
            val isNotice = msg.kind == IrcManager.MsgKind.NOTICE
            NanoVGRenderer.drawRoundedRect(x, y, w, h, 6f, NanoVGColor(1f, 1f, 1f, (0.035f + if (isOwn) 0.030f else 0f) * alpha))
            NanoVGRenderer.drawRoundedOutline(x, y, w, h, 6f, NanoVGColor(1f, 1f, 1f, (0.055f + if (isOwn) 0.06f else 0f) * alpha), 0.8f)
            val nickColor = when {
                isNotice -> NanoVGColor(0.98f, 0.72f, 0.25f, alpha)
                isOwn -> NanoVGColor(0.95f, 0.96f, 0.98f, alpha)
                else -> IrcChatModel.nickColor(msg.sender).withAlpha(alpha)
            }
            NanoVGRenderer.drawVerticallyCenteredString(msg.sender, x + 12f, y + 13f, nickColor, 9.5f, shadow = HUDModule.textShadow.value)
            NanoVGRenderer.drawRightVerticallyCenteredString(time, x + w - 12f, y + 13f, NanoVGColor(0.42f, 0.44f, 0.50f, 0.85f * alpha), 8f)
            val imageUrl = IrcImageCache.imageUrl(msg.text)
            if (imageUrl != null) {
                NanoVGRenderer.drawString("Image", x + 12f, y + 22f, NanoVGColor(0.78f, 0.80f, 0.84f, 0.90f * alpha), 8.5f)
                IrcImageCache.draw(imageUrl, x + 12f, y + 35f, w - 24f, IrcImageCache.previewHeight(), alpha)
            } else {
                val wrap = IrcChatModel.wrap(msg.text, w - 24f, 9.5f)
                val textColor = if (isNotice) NanoVGColor(0.98f, 0.85f, 0.55f, 0.92f * alpha) else NanoVGColor(0.88f, 0.90f, 0.94f, 0.90f * alpha)
                for (line in wrap.lines) {
                    NanoVGRenderer.drawString(line.text, x + 12f, y + 22f + line.yOffset, textColor, 9.5f, shadow = HUDModule.textShadow.value)
                }
            }
        }
        else -> {
            val color = when (msg.kind) {
                IrcManager.MsgKind.JOIN -> NanoVGColor(0.42f, 0.85f, 0.55f, 0.70f * alpha)
                IrcManager.MsgKind.LEAVE -> NanoVGColor(0.52f, 0.54f, 0.60f, 0.65f * alpha)
                else -> NanoVGColor(0.52f, 0.55f, 0.62f, 0.70f * alpha)
            }
            NanoVGRenderer.drawVerticallyCenteredString("· ${msg.sender} ${msg.text}".trim(), x + 6f, y + 12f, color, 8.5f)
        }
    }
}

private fun WindowClickGuiScreen.renderIrcInput(x: Float, y: Float, w: Float, h: Float, mx: Float, my: Float, alpha: Float) {
    ircInputFocusAnim.animateTo(if (ircInputFocused) 1f else 0f)
    val focus = ircInputFocusAnim.value
    NanoVGRenderer.drawRoundedRect(x, y, w, h, 7f, NanoVGColor(0f, 0f, 0f, (0.22f + focus * 0.10f) * alpha))
    NanoVGRenderer.drawRoundedOutline(x, y, w, h, 7f, NanoVGColor(1f, 1f, 1f, (0.10f + focus * 0.12f) * alpha), 1f)

    val textX = x + 12f
    val centerY = y + h * 0.5f
    val maxTextW = w - 46f

    if (ircInput.isEmpty() && !ircInputFocused) {
        NanoVGRenderer.drawVerticallyCenteredString("Type message... · Ctrl+V image", textX, centerY, NanoVGColor(0.50f, 0.51f, 0.55f, alpha), 9.5f)
    } else {
        NanoVGRenderer.withScissor(textX - 2f, y + 3f, maxTextW + 4f, h - 6f) {
            val textW = NanoVGRenderer.drawVerticallyCenteredString(ircInput, textX, centerY, NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 9.5f)
            if (ircInputFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
                val cursorX = (textX + textW + 1f).coerceAtMost(textX + maxTextW)
                Draw2D.line(cursorX, centerY - 5f, cursorX, centerY + 5f, NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 1f)
            }
        }
    }

    val sendSize = 20f
    val sendX = x + w - sendSize - 7f
    val sendY = y + 4f
    val canSend = IrcManager.isConnected && ircInput.isNotBlank()
    val sendHovered = inside(mx, my, sendX, sendY, sendSize, sendSize)
    val sendColor = when {
        canSend && sendHovered -> NanoVGColor(1f, 1f, 1f, 0.95f * alpha)
        canSend -> NanoVGColor(0.75f, 0.78f, 0.85f, 0.85f * alpha)
        else -> NanoVGColor(0.45f, 0.47f, 0.53f, 0.60f * alpha)
    }
    if (canSend) {
        NanoVGRenderer.drawRoundedRect(sendX, sendY, sendSize, sendSize, 5f, NanoVGColor(1f, 1f, 1f, if (sendHovered) 0.12f * alpha else 0.06f * alpha))
    }
    val path = cn.reverie.render.nanovg.NanoVGPath(RenderEngine.nanoVG.handle)
    val acx = sendX + sendSize * 0.5f
    val acy = sendY + sendSize * 0.5f
    path.begin()
    path.moveTo(acx - 4.2f, acy - 3.8f)
    path.lineTo(acx + 4.6f, acy)
    path.lineTo(acx - 4.2f, acy + 3.8f)
    path.lineTo(acx - 2.4f, acy)
    path.close()
    path.fill(sendColor)
}

private fun WindowClickGuiScreen.handleIrcOffersClick(mx: Float, my: Float, button: Int): Boolean {
    val cx = WindowClickGuiScreen.DETAIL_X
    val cw = WindowClickGuiScreen.DETAIL_W
    val top = 155f
    val entries = cn.reverie.nocturne.IrcShareManager.pendingOffers().take(5)
    if (button == 0 && mx in (cx + 14f)..(cx + cw - 14f) && my >= top + 26f) {
        val index = ((my - top - 26f) / 44f).toInt()
        val offer = entries.getOrNull(index)
        if (offer != null) {
            val ry = top + 26f + index * 44f
            when {
                mx in (cx + cw - 132f)..(cx + cw - 80f) && my in (ry + 10f)..(ry + 28f) -> {
                    when (cn.reverie.nocturne.IrcShareManager.acceptOffer(offer.id)) {
                        true -> net.minecraft.client.Minecraft.getInstance().execute { cn.reverie.util.misc.ChatUtil.sendInfo("Accepted share from ${offer.sender}") }
                        false -> net.minecraft.client.Minecraft.getInstance().execute { cn.reverie.util.misc.ChatUtil.sendError("Could not apply the shared item") }
                        null -> Unit
                    }
                    return true
                }
                mx in (cx + cw - 72f)..(cx + cw - 24f) && my in (ry + 10f)..(ry + 28f) -> {
                    cn.reverie.nocturne.IrcShareManager.rejectOffer(offer.id)
                    return true
                }
            }
        }
        return true
    }
    if (mx in (cx + 8f)..(cx + cw - 8f) && my in top..(top + 260f)) return true
    ircOffersOpen = false
    return false
}
