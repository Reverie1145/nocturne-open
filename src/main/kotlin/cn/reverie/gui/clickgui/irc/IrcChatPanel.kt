package cn.reverie.gui.clickgui.irc
import cn.reverie.nocturne.IrcManager
import cn.reverie.nocturne.NocturneAuth
import cn.reverie.nocturne.IrcShareManager
import cn.reverie.module.ModuleManager
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.animation.MotionTracker
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextLayout
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import java.awt.Graphics2D
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import cn.reverie.gui.clickgui.CategoryPanelChrome
import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.gui.clickgui.ClickGUIModule

internal object IrcClipboard {
    data class PasteResult(val imageHandled: Boolean, val text: String?)

    fun readImagePng(): ByteArray? {
        return try {
            val transferable = Toolkit.getDefaultToolkit().systemClipboard.getContents(null) ?: return null
            if (!transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) return null
            val source = transferable.getTransferData(DataFlavor.imageFlavor) as? Image ?: return null
            val width = source.getWidth(null)
            val height = source.getHeight(null)
            if (width <= 0 || height <= 0 || width.toLong() * height > 12_000_000L) return null
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics: Graphics2D = image.createGraphics()
            try {
                graphics.drawImage(source, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            ByteArrayOutputStream().use { out ->
                if (!ImageIO.write(image, "png", out)) null else out.toByteArray()
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun paste(target: String, currentText: String, maxLength: Int): PasteResult {
        val image = readImagePng()
        if (image != null) {
            IrcManager.uploadImage(image, target)
            return PasteResult(imageHandled = true, text = null)
        }
        val text = try {
            Minecraft.getInstance().keyboardHandler.clipboard
        } catch (_: Throwable) {
            ""
        }
        if (text.isEmpty()) return PasteResult(imageHandled = false, text = null)
        val normalized = text.replace("\r", "").replace("\n", " ")
        return PasteResult(imageHandled = false, text = (currentText + normalized).take(maxLength))
    }
}

object IrcChatModel {
    private val nickPalette = arrayOf(
        NanoVGColor(0.36f, 0.80f, 0.94f, 1f),
        NanoVGColor(0.98f, 0.72f, 0.25f, 1f),
        NanoVGColor(0.42f, 0.85f, 0.55f, 1f),
        NanoVGColor(0.72f, 0.60f, 0.98f, 1f),
        NanoVGColor(0.95f, 0.55f, 0.62f, 1f),
        NanoVGColor(0.55f, 0.78f, 0.98f, 1f),
        NanoVGColor(0.90f, 0.85f, 0.45f, 1f)
    )

    fun nickColor(nick: String): NanoVGColor {
        if (nick.isEmpty()) return NanoVGColor(0.55f, 0.58f, 0.65f, 1f)
        return nickPalette[kotlin.math.abs(nick.hashCode()) % nickPalette.size]
    }

    fun timeText(time: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = time
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }

    class Wrapped(val lines: List<TextLayout.Line>, val totalHeight: Float, val lineHeight: Float)

    private val wrapCache = object : LinkedHashMap<String, Wrapped>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Wrapped>?): Boolean = size > 256
    }

    fun wrap(text: String, maxWidth: Float, fontSize: Float): Wrapped {
        val key = "$fontSize|$maxWidth|$text"
        synchronized(wrapCache) {
            wrapCache[key]?.let { return it }
        }
        val font = RenderEngine.fontRenderer.activeFont
        val layout = TextLayout(
            font, text,
            fontSize = RenderEngine.fontRenderer.resolveSize(fontSize),
            maxWidth = maxWidth,
            lineSpacing = 2.5f
        )
        val wrapped = Wrapped(
            layout.lines, layout.totalHeight,
            if (layout.lines.size >= 2) layout.lines[1].yOffset - layout.lines[0].yOffset else layout.totalHeight
        )
        synchronized(wrapCache) {
            wrapCache[key] = wrapped
        }
        return wrapped
    }

    fun statusText(): String = when (IrcManager.state) {
        IrcManager.State.CONNECTED -> "${IrcManager.channelMembers().size} online"
        IrcManager.State.CONNECTING -> "Connecting…"
        IrcManager.State.AUTH_FAILED -> "Auth failed"
        IrcManager.State.IDLE -> "Offline"
    }

    fun contacts(): List<String> = IrcManager.channelMembers()
        .plus(IrcManager.privateContacts())
        .distinctBy { it.lowercase() }
        .filterNot { it.equals(NocturneAuth.username, ignoreCase = true) }
        .sortedBy { it.lowercase() }

    fun stateColor(): NanoVGColor = when (IrcManager.state) {
        IrcManager.State.CONNECTED -> NanoVGColor(0.42f, 0.85f, 0.55f, 1f)
        IrcManager.State.CONNECTING -> NanoVGColor(0.98f, 0.72f, 0.25f, 1f)
        IrcManager.State.AUTH_FAILED -> NanoVGColor(0.95f, 0.45f, 0.45f, 1f)
        IrcManager.State.IDLE -> NanoVGColor(0.55f, 0.58f, 0.65f, 1f)
    }

    fun send(text: String, target: String = IrcManager.channel): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val privateCommand = Regex("^/msg\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        return if (privateCommand != null) {
            IrcManager.send(privateCommand.groupValues[2], privateCommand.groupValues[1])
        } else {
            IrcManager.send(trimmed, target)
        }
    }
}

/**
 * Dropdown-style IRC Chat panel matching ClickGUI category panel design.
 */
class IrcChatPanel(initialX: Float, initialY: Float) {
    var x: Float = initialX
    var y: Float = initialY

    val width: Float
        get() = ClickGUIModule.panelWidth.getFloat()

    val headerHeight: Float
        get() = ClickGUIModule.headerHeight.getFloat()

    private val pad = 6f
    private val subBarHeight = 22f
    private val actionBarHeight = 18f
    private val messagesHeight = 222f
    private val inputHeight = 26f

    var isExpanded: Boolean = savedExpanded ?: true

    var isDragging: Boolean = false
        private set
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f

    var scrollAmount: Float = 0f
    var targetScroll: Float = 0f
    private var pinnedToBottom: Boolean = true

    var inputText: String = ""
    var inputFocused: Boolean = false
    var cursorTimer: Long = 0L
    var privateTarget: String? = null
        private set
    private var isContactPickerOpen: Boolean = false
    private var contactScroll: Int = 0
    private var modulePickerOpen: Boolean = false
    private var moduleScroll: Int = 0
    private var listeningOpen: Boolean = false
    private var offersOpen: Boolean = false
    private var pendingShareAction: String? = null
    private var lastImageClickAt: Long = 0L
    private var lastImageClickUrl: String? = null

    val expandAnim = FloatAnimation(
        if (isExpanded) 1f else 0f,
        if (isExpanded) 1f else 0f,
        340L,
        CubicBezier.EASE_IN_OUT
    )
    val headerHoverAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val inputFocusAnim = FloatAnimation(0f, 0f, 190L, CubicBezier.EASE_OUT)
    val dragElevateAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
    val contactPickerAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val modulePickerAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val listeningAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val offersAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    private val dragTracker = MotionTracker(accelFactor = 0.45f, decelFactor = 0.10f, deadzone = 0.02f)

    init {
        savedPosition?.let {
            x = it.first
            y = it.second
        }
        savedScroll?.let {
            if (it > 0f) {
                targetScroll = it
                scrollAmount = it
            }
        }
    }

    val contentHeight: Float
        get() = subBarHeight + actionBarHeight + messagesHeight + inputHeight + pad * 2f + 4f

    val maxVisibleContentHeight: Float
        get() {
            val screenH = ResolutionHelper.scaledHeight
            return (screenH - y - headerHeight - 20f).coerceIn(160f, 460f)
        }

    val visibleContentHeight: Float
        get() = minOf(contentHeight, maxVisibleContentHeight)

    val totalHeight: Float
        get() = headerHeight + visibleContentHeight * expandAnim.value

    private val messageTextWidth: Float
        get() = width - pad * 2f - 10f

    private fun messageVisualHeight(msg: IrcManager.IrcMessage): Float = when (msg.kind) {
        IrcManager.MsgKind.CHAT -> {
            if (IrcImageCache.imageUrl(msg.text) != null) 14f + 10f + IrcImageCache.previewHeight() + 6f
            else {
                val wrap = IrcChatModel.wrap(msg.text, messageTextWidth, 9.0f)
                14f + wrap.totalHeight + 6f
            }
        }
        else -> 14f
    }

    fun isMouseOverHeader(mouseX: Float, mouseY: Float): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + headerHeight

    fun isMouseOverTotal(mouseX: Float, mouseY: Float): Boolean =
        mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalHeight

    val actualMessagesHeight: Float
        get() = (visibleContentHeight - subBarHeight - actionBarHeight - inputHeight - pad * 2f - 4f).coerceAtLeast(80f)

    private fun inputRect(): Rect {
        val iy = y + headerHeight + pad + subBarHeight + actionBarHeight + actualMessagesHeight + 4f
        return Rect(x + pad, iy, width - pad * 2f, inputHeight)
    }

    private fun sendCurrentInput(): Boolean {
        val value = inputText.trim()
        if (IrcShareManager.handleCommand(value)) return true
        val directCommand = Regex("^/msg\\s+(\\S+)\\s+(.+)$", RegexOption.IGNORE_CASE).matchEntire(value)
        val target = directCommand?.groupValues?.get(1) ?: (privateTarget ?: IrcManager.channel)
        val body = directCommand?.groupValues?.get(2) ?: value
        if (body.isEmpty() || !IrcManager.send(body, target)) return false
        if (directCommand != null) privateTarget = target
        return true
    }

    private class Rect(val x: Float, val y: Float, val w: Float, val h: Float) {
        fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
    }

    fun jumpToBottom() {
        pinnedToBottom = true
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        expandAnim.durationMs = (240L / animMultiplier).toLong().coerceIn(80L, 600L)
        headerHoverAnim.durationMs = (140L / animMultiplier).toLong().coerceIn(40L, 350L)
        inputFocusAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(50L, 400L)
        dragElevateAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(50L, 400L)
        contactPickerAnim.durationMs = (190L / animMultiplier).toLong().coerceIn(50L, 400L)
        modulePickerAnim.durationMs = (190L / animMultiplier).toLong().coerceIn(50L, 400L)
        listeningAnim.durationMs = (190L / animMultiplier).toLong().coerceIn(50L, 400L)
        offersAnim.durationMs = (190L / animMultiplier).toLong().coerceIn(50L, 400L)

        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        headerHoverAnim.animateTo(if (isMouseOverHeader(mouseX, mouseY)) 1f else 0f)
        inputFocusAnim.animateTo(if (inputFocused) 1f else 0f)
        dragElevateAnim.animateTo(if (isDragging) 1f else 0f)
        contactPickerAnim.animateTo(if (isContactPickerOpen) 1f else 0f)
        modulePickerAnim.animateTo(if (modulePickerOpen) 1f else 0f)
        listeningAnim.animateTo(if (listeningOpen) 1f else 0f)
        offersAnim.animateTo(if (offersOpen) 1f else 0f)

        val currentW = width
        val currentH = totalHeight
        val cornerRadius = ClickGUIModule.cornerRadius.getFloat()

        dragTracker.update(x, y)

        if (dragTracker.isMoving && dragElevateAnim.value > 0.01f) {
            cn.reverie.render.animation.MotionBlurHelper.renderMotionBlur(
                x, y, currentW, currentH,
                cornerRadius,
                dragTracker.vx, dragTracker.vy,
                NanoVGColor(1f, 1f, 1f, 0.25f * dragElevateAnim.value),
                maxBlur = 18f,
                samples = 4
            )
        }

        if (HUDModule.widgetShadow.value) {
            Draw2D.shadow(
                x, y, currentW, currentH, cornerRadius, 10f + 8f * dragElevateAnim.value,
                NanoVGColor(0f, 0f, 0f, (0.35f + 0.15f * dragElevateAnim.value) * HUDModule.shadowStrength.getFloat())
            )
        }

        val panelAlpha = HUDModule.bgOpacity.getFloat()
        val panelBg = NanoVGColor(0.047f, 0.051f, 0.067f, panelAlpha)
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                x, y, currentW, currentH,
                radius = cornerRadius,
                tintColor = panelBg,
                blurRadius = HUDModule.blurRadius.getFloat(),
                enableBlur = HUDModule.blur.value,
                backdropOpacity = panelAlpha
            )
        } catch (_: Throwable) {
            NanoVGRenderer.drawRoundedRect(x, y, currentW, currentH, cornerRadius, panelBg)
        }

        CategoryPanelChrome.draw(x, y, currentW, currentH, headerHeight, cornerRadius, headerHoverAnim.value, expandAnim.value)

        // Header Icon
        val iconSize = 16f
        val iconX = x + 10f
        val iconY = y + (headerHeight - iconSize) * 0.5f
        val iconColor = NanoVGColor(0.85f, 0.88f, 0.92f, 0.90f).lerp(NanoVGColor.WHITE, headerHoverAnim.value)
        CategoryVectorIcons.drawChatIcon(iconX + iconSize * 0.5f, iconY + iconSize * 0.5f, iconSize, iconColor)

        // Title: IRC or IRC · @user
        val titleX = iconX + iconSize + 8f
        val titleText = privateTarget?.let { "IRC · @$it" } ?: "IRC"
        val titleBlur = if (expandAnim.isRunning) kotlin.math.sin(expandAnim.value * Math.PI).toFloat() * 3.8f else 0f
        val titleW = NanoVGRenderer.drawVerticallyCenteredString(
            titleText.uppercase(), titleX, y + headerHeight * 0.5f,
            NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f), 11.5f,
            shadow = HUDModule.textShadow.value,
            blur = titleBlur
        )

        // Connection status dot right next to title
        val dotX = titleX + titleW + 7f
        val dotY = y + headerHeight * 0.5f
        NanoVGRenderer.drawCircle(dotX, dotY, 2.4f, IrcChatModel.stateColor())

        // Chevron down
        val chevronColor = NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f).lerp(NanoVGColor.WHITE, headerHoverAnim.value * 0.4f)
        CategoryVectorIcons.drawChevronDown(x + currentW - 12f, y + headerHeight * 0.5f, 7f, 1f - expandAnim.value, chevronColor)

        if (expandAnim.value > 0.01f) {
            val fade = expandAnim.value
            val contentBoxH = currentH - headerHeight

            NanoVGRenderer.withScissor(x, y + headerHeight + 1f, currentW, contentBoxH) {
                // 1. Channel / DM Status Bar
                val subY = y + headerHeight + 4f
                val subW = currentW - pad * 2f
                NanoVGRenderer.drawRoundedRect(x + pad, subY, subW, subBarHeight, 3.5f, NanoVGColor(1f, 1f, 1f, 0.025f * fade))

                val targetTag = if (privateTarget != null) "@${privateTarget}" else "#${IrcManager.channel}"
                NanoVGRenderer.drawVerticallyCenteredString(
                    targetTag, x + pad + 6f, subY + subBarHeight * 0.5f,
                    HUDModule.sampleColor(0f).withAlpha(0.95f * fade), 8.5f,
                    shadow = HUDModule.textShadow.value
                )

                val statusStr = IrcChatModel.statusText()
                val statusW = NanoVGRenderer.getTextWidth(statusStr, 7.5f)

                // DM button on the right
                val dmBtnW = 28f
                val dmBtnH = 14f
                val dmBtnX = x + pad + subW - dmBtnW - 4f
                val dmBtnY = subY + (subBarHeight - dmBtnH) * 0.5f
                val dmHover = mouseX in dmBtnX..(dmBtnX + dmBtnW) && mouseY in dmBtnY..(dmBtnY + dmBtnH)

                Draw2D.roundedRect(dmBtnX, dmBtnY, dmBtnW, dmBtnH, 3f,
                    if (dmHover || isContactPickerOpen) NanoVGColor(1f, 1f, 1f, 0.12f * fade) else NanoVGColor(1f, 1f, 1f, 0.05f * fade))
                NanoVGRenderer.drawCenteredString("DM", dmBtnX + dmBtnW * 0.5f, dmBtnY + dmBtnH * 0.5f,
                    if (dmHover || isContactPickerOpen) NanoVGColor.WHITE else NanoVGColor(0.80f, 0.83f, 0.88f, 0.85f * fade), 7.5f)

                // If in DM mode, show back to channel button
                if (privateTarget != null) {
                    val backBtnW = 42f
                    val backBtnX = dmBtnX - backBtnW - 4f
                    val backHover = mouseX in backBtnX..(backBtnX + backBtnW) && mouseY in dmBtnY..(dmBtnY + dmBtnH)
                    Draw2D.roundedRect(backBtnX, dmBtnY, backBtnW, dmBtnH, 3f,
                        if (backHover) NanoVGColor(1f, 1f, 1f, 0.12f * fade) else NanoVGColor(1f, 1f, 1f, 0.05f * fade))
                    NanoVGRenderer.drawCenteredString("Channel", backBtnX + backBtnW * 0.5f, dmBtnY + dmBtnH * 0.5f,
                        if (backHover) NanoVGColor.WHITE else NanoVGColor(0.80f, 0.83f, 0.88f, 0.85f * fade), 7.5f)
                } else {
                    NanoVGRenderer.drawRightVerticallyCenteredString(
                        statusStr, dmBtnX - 6f, subY + subBarHeight * 0.5f,
                        NanoVGColor(0.50f, 0.53f, 0.60f, 0.85f * fade), 7.5f
                    )
                }

                val actionY = subY + subBarHeight + 2f
                renderShareToolbar(actionY, mouseX, mouseY, fade)

                // 2. Messages List
                val msgAreaX = x + pad
                val msgAreaY = actionY + actionBarHeight + 2f
                val msgAreaW = currentW - pad * 2f

                val messages = IrcManager.messagesFor(privateTarget ?: IrcManager.channel, direct = privateTarget != null)
                var totalMsgH = 0f
                for (msg in messages) totalMsgH += messageVisualHeight(msg) + 3f

                val curMsgH = actualMessagesHeight
                val maxScroll = (totalMsgH - curMsgH).coerceAtLeast(0f)
                if (pinnedToBottom) targetScroll = maxScroll
                targetScroll = targetScroll.coerceIn(0f, maxScroll)
                val animLerp = (0.30f * animMultiplier).coerceIn(0.1f, 1f)
                scrollAmount += (targetScroll - scrollAmount) * animLerp
                if (kotlin.math.abs(targetScroll - scrollAmount) < 0.05f) scrollAmount = targetScroll
                pinnedToBottom = maxScroll - scrollAmount < 8f

                NanoVGRenderer.withScissor(msgAreaX, msgAreaY, msgAreaW, curMsgH) {
                    var msgY = msgAreaY - scrollAmount
                    for (msg in messages) {
                        val itemH = messageVisualHeight(msg)
                        if (msgY + itemH >= msgAreaY - 4f && msgY <= msgAreaY + curMsgH + 4f) {
                            renderMessage(msg, msgAreaX + 2f, msgY, msgAreaW - 4f, fade)
                        }
                        msgY += itemH + 3f
                    }
                }

                // Scrollbar
                if (maxScroll > 0f) {
                    val trackX = x + currentW - 3.5f
                    val trackY = msgAreaY + 2f
                    val trackH = curMsgH - 4f
                    val thumbH = (curMsgH / totalMsgH * trackH).coerceAtLeast(16f)
                    val thumbY = trackY + (scrollAmount / maxScroll).coerceIn(0f, 1f) * (trackH - thumbH)
                    NanoVGRenderer.drawRoundedRect(trackX, trackY, 1.5f, trackH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.05f))
                    NanoVGRenderer.drawRoundedRect(trackX, thumbY, 1.5f, thumbH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.25f))
                }

                // 3. Input Field
                val input = inputRect()
                renderInput(input, mouseX, mouseY, fade)

                // 4. Popups (smoothly animated)
                if (contactPickerAnim.value > 0.005f) {
                    renderContactPicker(mouseX, mouseY, fade * contactPickerAnim.value, contactPickerAnim.value)
                }
                if (modulePickerAnim.value > 0.005f) renderModulePicker(mouseX, mouseY, fade * modulePickerAnim.value, actionY, modulePickerAnim.value)
                if (listeningAnim.value > 0.005f) renderListeningPanel(fade * listeningAnim.value, actionY, listeningAnim.value)
                if (offersAnim.value > 0.005f) renderOffersPanel(mouseX, mouseY, fade * offersAnim.value, actionY, offersAnim.value)
            }
        }
    }

    private fun toolbarButtons(): List<Pair<String, Rect>> {
        val bw = 64f
        val bx = x + width - pad - bw
        return listOf("Inbox ${IrcShareManager.pendingOffers().size}" to Rect(bx, y + headerHeight + 4f + subBarHeight + 2f, bw, actionBarHeight))
    }

    private fun renderShareToolbar(actionY: Float, mouseX: Float, mouseY: Float, fade: Float) {
        val buttons = toolbarButtons()
        val actions = listOf("offers")
        for (index in buttons.indices) {
            val (label, rect) = buttons[index]
            val action = actions[index]
            val hovered = rect.contains(mouseX, mouseY)
            val active = when (action) {
                "offers" -> offersOpen
                else -> false
            }
            NanoVGRenderer.drawRoundedRect(
                rect.x, actionY, rect.w, actionBarHeight - 1f, 3.2f,
                NanoVGColor(1f, 1f, 1f, (if (active) 0.13f else if (hovered) 0.10f else 0.045f) * fade)
            )
            NanoVGRenderer.drawCenteredString(
                label, rect.x + rect.w * 0.5f, actionY + (actionBarHeight - 1f) * 0.5f,
                NanoVGColor(0.86f, 0.89f, 0.94f, (if (active || hovered) 0.98f else 0.80f) * fade), 7.3f
            )
        }
    }

    private fun renderGlassPopup(px: Float, py: Float, w: Float, h: Float, radius: Float, fade: Float) {
        val pAlpha = HUDModule.bgOpacity.getFloat() * fade
        val pBg = NanoVGColor(0.047f, 0.051f, 0.067f, pAlpha)
        if (HUDModule.widgetShadow.value) {
            Draw2D.shadow(px, py, w, h, radius, 10f, NanoVGColor(0f, 0f, 0f, 0.35f * HUDModule.shadowStrength.getFloat() * fade))
        }
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                px, py, w, h,
                radius = radius,
                tintColor = pBg,
                blurRadius = HUDModule.blurRadius.getFloat(),
                enableBlur = HUDModule.blur.value,
                backdropOpacity = pAlpha
            )
        } catch (_: Throwable) {
            NanoVGRenderer.drawRoundedRect(px, py, w, h, radius, pBg)
        }
        NanoVGRenderer.drawRoundedOutline(px, py, w, h, radius, NanoVGColor(1f, 1f, 1f, 0.09f * fade), 0.8f)
    }

    private fun renderModulePicker(mouseX: Float, mouseY: Float, fade: Float, actionY: Float, progress: Float = 1f) {
        val modules = ModuleManager.modules.sortedBy { it.name }
        val rowHeight = 18f
        val visible = minOf(6, modules.size)
        val w = width - pad * 2f
        val px = x + pad
        val py = actionY + actionBarHeight + 2f - (1f - progress) * 5f
        val h = 20f + visible * rowHeight
        renderGlassPopup(px, py, w, h, 5f, fade)
        NanoVGRenderer.drawString("Select a module to share", px + 7f, py + 5f, NanoVGColor(0.65f, 0.68f, 0.74f, fade), 7.4f)
        NanoVGRenderer.withScissor(px + 3f, py + 18f, w - 6f, visible * rowHeight) {
            for (index in moduleScroll until minOf(modules.size, moduleScroll + visible)) {
                val rowY = py + 18f + (index - moduleScroll) * rowHeight
                val hovered = mouseX in (px + 3f)..(px + w - 3f) && mouseY in rowY..(rowY + rowHeight)
                if (hovered) NanoVGRenderer.drawRoundedRect(px + 3f, rowY, w - 6f, rowHeight, 2.5f, NanoVGColor(1f, 1f, 1f, 0.08f * fade))
                NanoVGRenderer.drawVerticallyCenteredString(modules[index].name, px + 8f, rowY + rowHeight * 0.5f,
                    NanoVGColor(0.88f, 0.90f, 0.94f, fade), 7.6f)
            }
        }
    }

    private fun renderListeningPanel(fade: Float, actionY: Float, progress: Float = 1f) {
        val entries = IrcShareManager.nowPlayingEntries().take(7)
        val rowHeight = 22f
        val w = width - pad * 2f
        val px = x + pad
        val py = actionY + actionBarHeight + 2f - (1f - progress) * 5f
        val h = 23f + maxOf(1, entries.size) * rowHeight
        renderGlassPopup(px, py, w, h, 5f, fade)
        NanoVGRenderer.drawString("Now playing · ${entries.size}", px + 7f, py + 6f, NanoVGColor(0.68f, 0.83f, 0.75f, fade), 7.5f)
        if (entries.isEmpty()) {
            NanoVGRenderer.drawString("No live music status", px + 7f, py + 29f, NanoVGColor(0.58f, 0.61f, 0.68f, fade), 7.5f)
        } else {
            entries.forEachIndexed { index, entry ->
                val rowY = py + 22f + index * rowHeight
                NanoVGRenderer.drawVerticallyCenteredString("${entry.nick} · ${entry.title} - ${entry.artists}".take(68), px + 7f,
                    rowY + rowHeight * 0.5f, NanoVGColor(0.88f, 0.90f, 0.94f, fade), 7.2f)
                NanoVGRenderer.drawRightVerticallyCenteredString(if (entry.state == "PAUSED") "Paused" else "Playing",
                    px + w - 7f, rowY + rowHeight * 0.5f, NanoVGColor(0.55f, 0.77f, 0.64f, fade), 6.8f)
            }
        }
    }

    private fun renderOffersPanel(mouseX: Float, mouseY: Float, fade: Float, actionY: Float, progress: Float = 1f) {
        val entries = IrcShareManager.pendingOffers().take(5)
        val rowHeight = 30f
        val w = width - pad * 2f
        val px = x + pad
        val py = actionY + actionBarHeight + 2f - (1f - progress) * 5f
        val h = 22f + maxOf(1, entries.size) * rowHeight
        renderGlassPopup(px, py, w, h, 5f, fade)
        NanoVGRenderer.drawString("Incoming shares · ${entries.size}", px + 7f, py + 5f, NanoVGColor(0.88f, 0.76f, 0.55f, fade), 7.4f)
        if (entries.isEmpty()) {
            NanoVGRenderer.drawString("No pending items", px + 7f, py + 27f, NanoVGColor(0.58f, 0.61f, 0.68f, fade), 7.3f)
        } else {
            entries.forEachIndexed { index, offer ->
                val rowY = py + 19f + index * rowHeight
                val kind = when (offer.kind) { "config" -> "Config"; "module" -> "Module config"; else -> "Music" }
                NanoVGRenderer.drawVerticallyCenteredString("${offer.sender} · $kind", px + 7f, rowY + rowHeight * 0.5f,
                    NanoVGColor(0.86f, 0.88f, 0.92f, fade), 7.2f)
                val accept = Rect(px + w - 60f, rowY + 5f, 27f, 19f)
                val reject = Rect(px + w - 30f, rowY + 5f, 25f, 19f)
                NanoVGRenderer.drawRoundedRect(accept.x, accept.y, accept.w, accept.h, 3f,
                    NanoVGColor(0.28f, 0.65f, 0.42f, (if (accept.contains(mouseX, mouseY)) 0.9f else 0.62f) * fade))
                NanoVGRenderer.drawCenteredString("Accept", accept.x + accept.w * 0.5f, accept.y + accept.h * 0.5f, NanoVGColor.WHITE.withAlpha(fade), 6.5f)
                NanoVGRenderer.drawRoundedRect(reject.x, reject.y, reject.w, reject.h, 3f,
                    NanoVGColor(0.55f, 0.25f, 0.27f, (if (reject.contains(mouseX, mouseY)) 0.9f else 0.62f) * fade))
                NanoVGRenderer.drawCenteredString("Reject", reject.x + reject.w * 0.5f, reject.y + reject.h * 0.5f, NanoVGColor.WHITE.withAlpha(fade), 6.5f)
            }
        }
    }

    private fun renderContactPicker(mouseX: Float, mouseY: Float, fade: Float, progress: Float = 1f) {
        val contacts = IrcChatModel.contacts()
        val rowHeight = 18f
        val rows = listOf(IrcManager.channel) + contacts
        val visibleRows = minOf(4, rows.size)
        val songs = IrcShareManager.nowPlayingEntries().take(3)
        val pickerW = minOf(140f, width - 14f)
        val pickerX = x + width - pickerW - 6f
        val pickerY = y + headerHeight + subBarHeight + actionBarHeight + 6f - (1f - progress) * 5f
        val musicH = 17f + maxOf(1, songs.size) * 17f
        val pickerH = 22f + visibleRows * rowHeight + 6f + musicH

        renderGlassPopup(pickerX, pickerY, pickerW, pickerH, 5f, fade)
        NanoVGRenderer.drawString("Direct Messages", pickerX + 8f, pickerY + 6f, NanoVGColor(0.58f, 0.61f, 0.68f, fade), 7.5f)

        val maxScroll = (rows.size - visibleRows).coerceAtLeast(0)
        contactScroll = contactScroll.coerceIn(0, maxScroll)

        NanoVGRenderer.withScissor(pickerX + 3f, pickerY + 18f, pickerW - 6f, visibleRows * rowHeight) {
            for (index in contactScroll until minOf(rows.size, contactScroll + visibleRows)) {
                val row = rows[index]
                val rowY = pickerY + 18f + (index - contactScroll) * rowHeight
                val isChannel = index == 0
                val selected = if (isChannel) privateTarget == null else privateTarget.equals(row, ignoreCase = true)
                val hovered = mouseX in (pickerX + 3f)..(pickerX + pickerW - 3f) && mouseY in rowY..(rowY + rowHeight)
                if (selected || hovered) {
                    NanoVGRenderer.drawRoundedRect(pickerX + 3f, rowY, pickerW - 6f, rowHeight, 3f,
                        NanoVGColor(1f, 1f, 1f, (if (selected) 0.085f else 0.045f) * fade))
                }
                NanoVGRenderer.drawVerticallyCenteredString(
                    if (isChannel) "#${row} (Channel)" else "@$row",
                    pickerX + 8f, rowY + rowHeight * 0.5f,
                    NanoVGColor(0.85f, 0.88f, 0.92f, fade), 8f
                )
            }
        }
        val musicY = pickerY + 20f + visibleRows * rowHeight + 4f
        NanoVGRenderer.drawString("Now playing", pickerX + 8f, musicY, NanoVGColor(0.68f, 0.83f, 0.75f, fade), 7f)
        if (songs.isEmpty()) {
            NanoVGRenderer.drawString("No live music", pickerX + 8f, musicY + 12f, NanoVGColor(0.55f, 0.57f, 0.63f, fade), 7f)
        } else songs.forEachIndexed { index, entry ->
            val lineY = musicY + 12f + index * 17f
            NanoVGRenderer.withScissor(pickerX + 6f, lineY, pickerW - 12f, 16f) {
                NanoVGRenderer.drawVerticallyCenteredString("${entry.nick} · ${entry.title} - ${entry.artists}", pickerX + 8f, lineY + 8f,
                    NanoVGColor(0.84f, 0.86f, 0.90f, fade), 6.8f)
            }
        }
    }

    private fun activateToolbarAction(action: String) {
        modulePickerOpen = false
        when (action) {
            "config", "module", "music" -> {
                listeningOpen = false
                offersOpen = false
                if (privateTarget == null) {
                    pendingShareAction = action
                    isContactPickerOpen = true
                } else dispatchShareAction(action)
            }
            "listening" -> {
                offersOpen = false
                listeningOpen = !listeningOpen
                if (listeningOpen) IrcShareManager.requestNowPlaying()
            }
            "offers" -> {
                listeningOpen = false
                offersOpen = !offersOpen
            }
        }
    }

    private fun dispatchShareAction(action: String) {
        val target = privateTarget ?: return
        when (action) {
            "config" -> IrcShareManager.sendCurrentConfig(target)
            "module" -> modulePickerOpen = true
            "music" -> IrcShareManager.shareCurrentMusic(target)
        }
    }

    private fun renderMessage(msg: IrcManager.IrcMessage, mx: Float, my: Float, mw: Float, fade: Float) {
        val time = IrcChatModel.timeText(msg.time)
        when (msg.kind) {
            IrcManager.MsgKind.CHAT -> {
                val isOwn = msg.sender.equals(NocturneAuth.username, ignoreCase = true)
                val nickColor = if (isOwn) NanoVGColor(0.95f, 0.96f, 0.98f, 1f) else IrcChatModel.nickColor(msg.sender)
                if (isOwn) {
                    NanoVGRenderer.drawRoundedRect(mx - 2f, my - 1f, mw + 4f, messageVisualHeight(msg) + 2f, 4f, NanoVGColor(1f, 1f, 1f, 0.035f * fade))
                }
                NanoVGRenderer.drawVerticallyCenteredString(msg.sender, mx, my + 6f, nickColor.withAlpha(fade), 9f, shadow = HUDModule.textShadow.value)
                NanoVGRenderer.drawRightVerticallyCenteredString(time, mx + mw, my + 6f, NanoVGColor(0.42f, 0.44f, 0.50f, 0.80f * fade), 7.5f)
                val textAlpha = if (isOwn) 0.95f * fade else 0.85f * fade
                val imageUrl = IrcImageCache.imageUrl(msg.text)
                if (imageUrl != null) {
                    NanoVGRenderer.drawString("Image", mx, my + 14f, NanoVGColor(0.78f, 0.80f, 0.84f, textAlpha), 8f)
                    IrcImageCache.draw(imageUrl, mx, my + 24f, mw, IrcImageCache.previewHeight(), fade)
                } else {
                    val wrap = IrcChatModel.wrap(msg.text, messageTextWidth, 9.0f)
                    for (line in wrap.lines) {
                        NanoVGRenderer.drawString(line.text, mx, my + 14f + line.yOffset, NanoVGColor(0.88f, 0.90f, 0.94f, textAlpha), 9f, shadow = HUDModule.textShadow.value)
                    }
                }
            }
            IrcManager.MsgKind.NOTICE -> {
                NanoVGRenderer.drawVerticallyCenteredString(msg.sender, mx, my + 6f, NanoVGColor(0.98f, 0.72f, 0.25f, 0.95f * fade), 9f, shadow = HUDModule.textShadow.value)
                NanoVGRenderer.drawRightVerticallyCenteredString(time, mx + mw, my + 6f, NanoVGColor(0.42f, 0.44f, 0.50f, 0.80f * fade), 7.5f)
                val wrap = IrcChatModel.wrap(msg.text, messageTextWidth, 9.0f)
                for (line in wrap.lines) {
                    NanoVGRenderer.drawString(line.text, mx, my + 14f + line.yOffset, NanoVGColor(0.98f, 0.85f, 0.55f, 0.90f * fade), 9f, shadow = HUDModule.textShadow.value)
                }
            }
            IrcManager.MsgKind.JOIN -> {
                NanoVGRenderer.drawVerticallyCenteredString("· ${msg.sender} ${msg.text}", mx, my + 6f, NanoVGColor(0.42f, 0.85f, 0.55f, 0.70f * fade), 8f)
            }
            IrcManager.MsgKind.LEAVE -> {
                NanoVGRenderer.drawVerticallyCenteredString("· ${msg.sender} ${msg.text}", mx, my + 6f, NanoVGColor(0.52f, 0.54f, 0.60f, 0.65f * fade), 8f)
            }
            IrcManager.MsgKind.SYSTEM -> {
                NanoVGRenderer.drawVerticallyCenteredString(msg.text, mx, my + 6f, NanoVGColor(0.52f, 0.55f, 0.62f, 0.70f * fade), 8f)
            }
        }
    }

    private fun renderInput(input: Rect, mouseX: Float, mouseY: Float, fade: Float) {
        val focus = inputFocusAnim.value
        val hovered = input.contains(mouseX, mouseY)
        val bgAlpha = (0.22f + focus * 0.10f) * fade
        NanoVGRenderer.drawRoundedRect(input.x, input.y, input.w, input.h, 5f, NanoVGColor(0f, 0f, 0f, bgAlpha))
        NanoVGRenderer.drawRoundedOutline(
            input.x, input.y, input.w, input.h, 5f,
            NanoVGColor(1f, 1f, 1f, (0.10f + focus * 0.12f + if (hovered && focus < 0.5f) 0.04f else 0f) * fade), 0.8f
        )

        val inputTextX = input.x + 8f
        val centerY = input.y + input.h * 0.5f
        val maxTextW = input.w - 32f

        if (inputText.isEmpty() && !inputFocused) {
            val targetName = privateTarget?.let { "@$it" } ?: ("#" + IrcManager.channel)
            NanoVGRenderer.drawVerticallyCenteredString(
                "Message $targetName... Ctrl+V image", inputTextX, centerY,
                NanoVGColor(0.42f, 0.44f, 0.50f, 0.60f * fade), 8.5f
            )
        } else {
            NanoVGRenderer.withScissor(inputTextX - 2f, input.y + 2f, maxTextW + 4f, input.h - 4f) {
                val textW = NanoVGRenderer.drawVerticallyCenteredString(inputText, inputTextX, centerY, NanoVGColor(0.95f, 0.96f, 0.98f, fade), 8.5f)
                if (inputFocused && (System.currentTimeMillis() - cursorTimer) / 500L % 2L == 0L) {
                    val cursorX = (inputTextX + textW + 1f).coerceAtMost(inputTextX + maxTextW)
                    Draw2D.line(cursorX, centerY - 4f, cursorX, centerY + 4f, NanoVGColor(0.95f, 0.95f, 0.98f, 0.9f * fade), 1f)
                }
            }
        }

        val sendBtnSize = 16f
        val sendBtnX = input.x + input.w - sendBtnSize - 5f
        val sendBtnY = input.y + (input.h - sendBtnSize) * 0.5f
        val canSend = IrcManager.isConnected && inputText.isNotBlank()
        val sendHovered = mouseX in sendBtnX..(sendBtnX + sendBtnSize) && mouseY in sendBtnY..(sendBtnY + sendBtnSize)
        val sendColor = when {
            canSend && sendHovered -> NanoVGColor(1f, 1f, 1f, 0.95f * fade)
            canSend -> NanoVGColor(0.75f, 0.78f, 0.85f, 0.85f * fade)
            else -> NanoVGColor(0.45f, 0.47f, 0.53f, 0.60f * fade)
        }
        if (canSend) {
            NanoVGRenderer.drawRoundedRect(sendBtnX, sendBtnY, sendBtnSize, sendBtnSize, 4f, NanoVGColor(1f, 1f, 1f, if (sendHovered) 0.12f * fade else 0.06f * fade))
        }
        drawSendArrow(sendBtnX + sendBtnSize * 0.5f, sendBtnY + sendBtnSize * 0.5f, sendColor, fade)
    }

    private fun drawSendArrow(cx: Float, cy: Float, color: NanoVGColor, fade: Float) {
        val path = cn.reverie.render.nanovg.NanoVGPath(RenderEngine.nanoVG.handle)
        path.begin()
        path.moveTo(cx - 3.8f, cy - 3.4f)
        path.lineTo(cx + 4.2f, cy)
        path.lineTo(cx - 3.8f, cy + 3.4f)
        path.lineTo(cx - 2.0f, cy)
        path.close()
        path.fill(color.withAlpha(color.a * fade))
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int, doubleClick: Boolean = false): Boolean {
        if (isMouseOverHeader(mouseX, mouseY)) {
            if (button == 0) {
                isDragging = true
                dragOffsetX = mouseX - x
                dragOffsetY = mouseY - y
                return true
            } else if (button == 1) {
                isExpanded = !isExpanded
                savedExpanded = isExpanded
                return true
            }
        }

        if (isExpanded && expandAnim.value > 0.2f) {
            val contentBoxH = totalHeight - headerHeight
            if (mouseX in x..(x + width) && mouseY in (y + headerHeight)..(y + headerHeight + contentBoxH)) {
                val subY = y + headerHeight + 4f
                val subW = width - pad * 2f

                // DM button click
                val dmBtnW = 28f
                val dmBtnH = 14f
                val dmBtnX = x + pad + subW - dmBtnW - 4f
                val dmBtnY = subY + (subBarHeight - dmBtnH) * 0.5f

                if (button == 0) {
                    val actions = listOf("offers")
                    val actionIndex = toolbarButtons().indexOfFirst { it.second.contains(mouseX, mouseY) }
                    if (actionIndex >= 0) {
                        activateToolbarAction(actions[actionIndex])
                        return true
                    }
                }

                if (modulePickerOpen) {
                    val modules = ModuleManager.modules
                    val visible = minOf(8, modules.size)
                    val px = x + pad + 40f
                    val py = y + headerHeight + 4f + subBarHeight + 2f + actionBarHeight + 2f
                    val pw = minOf(150f, width - 16f)
                    val rowHeight = 17f
                    val rowIndex = ((mouseY - (py + 18f)) / rowHeight).toInt() + moduleScroll
                    if (button == 0 && mouseX in px..(px + pw) && rowIndex in moduleScroll until minOf(modules.size, moduleScroll + visible)) {
                        privateTarget?.let { IrcShareManager.sendModuleConfig(it, modules[rowIndex].name) }
                        modulePickerOpen = false
                        return true
                    }
                    if (mouseX !in px..(px + pw) || mouseY !in py..(py + 20f + visible * rowHeight)) modulePickerOpen = false
                }

                if (listeningOpen && button == 0) {
                    val entries = IrcShareManager.nowPlayingEntries().take(7)
                    val panelY = y + headerHeight + 4f + subBarHeight + 2f + actionBarHeight + 2f
                    val panelH = 23f + maxOf(1, entries.size) * 22f
                    if (mouseX !in (x + pad)..(x + width - pad) || mouseY !in panelY..(panelY + panelH)) listeningOpen = false
                }

                if (offersOpen) {
                    val entries = IrcShareManager.pendingOffers().take(5)
                    val px = x + pad
                    val py = y + headerHeight + 4f + subBarHeight + 2f + actionBarHeight + 2f
                    val pw = width - pad * 2f
                    val rowHeight = 30f
                    if (button == 0 && mouseX in px..(px + pw) && mouseY >= py + 19f) {
                        val index = ((mouseY - (py + 19f)) / rowHeight).toInt()
                        val offer = entries.getOrNull(index)
                        if (offer != null) {
                            val acceptX = px + pw - 60f
                            val rejectX = px + pw - 30f
                            if (mouseX in acceptX..(acceptX + 27f)) {
                                when (IrcShareManager.acceptOffer(offer.id)) {
                                    true -> net.minecraft.client.Minecraft.getInstance().execute { cn.reverie.util.misc.ChatUtil.sendInfo("Accepted share from ${offer.sender}") }
                                    false -> net.minecraft.client.Minecraft.getInstance().execute { cn.reverie.util.misc.ChatUtil.sendError("Could not apply the shared item") }
                                    null -> Unit
                                }
                                return true
                            }
                            if (mouseX in rejectX..(rejectX + 25f)) {
                                IrcShareManager.rejectOffer(offer.id)
                                return true
                            }
                        }
                    }
                    val panelH = 22f + maxOf(1, entries.size) * rowHeight
                    if (mouseX !in px..(px + pw) || mouseY !in py..(py + panelH)) offersOpen = false
                }

                if (button == 0 && mouseX in dmBtnX..(dmBtnX + dmBtnW) && mouseY in dmBtnY..(dmBtnY + dmBtnH)) {
                    isContactPickerOpen = !isContactPickerOpen
                    return true
                }

                // Channel back button click
                if (privateTarget != null) {
                    val backBtnW = 42f
                    val backBtnX = dmBtnX - backBtnW - 4f
                    if (button == 0 && mouseX in backBtnX..(backBtnX + backBtnW) && mouseY in dmBtnY..(dmBtnY + dmBtnH)) {
                        privateTarget = null
                        isContactPickerOpen = false
                        jumpToBottom()
                        return true
                    }
                }

                // Contact picker selection
                if (isContactPickerOpen) {
                    val contacts = IrcChatModel.contacts()
                    val rows = listOf(IrcManager.channel) + contacts
                    val visibleRows = minOf(4, rows.size)
                    val pickerW = minOf(140f, width - 14f)
                    val pickerX = x + width - pickerW - 6f
                    val pickerY = y + headerHeight + subBarHeight + actionBarHeight + 6f
                    val rowHeight = 18f
                    val rowIndex = ((mouseY - (pickerY + 18f)) / rowHeight).toInt() + contactScroll

                    if (button == 0 && mouseX in pickerX..(pickerX + pickerW) && rowIndex in 0 until minOf(rows.size, contactScroll + visibleRows) && mouseY < pickerY + 18f + visibleRows * rowHeight) {
                        val pendingAction = pendingShareAction
                        if (pendingAction != null && rowIndex == 0) {
                            cn.reverie.util.misc.ChatUtil.sendWarning("Shares can only be sent to direct-message contacts")
                            return true
                        }
                        privateTarget = if (rowIndex == 0) null else rows[rowIndex]
                        isContactPickerOpen = false
                        inputFocused = true
                        cursorTimer = System.currentTimeMillis()
                        jumpToBottom()
                        pendingShareAction = null
                        pendingAction?.let(::dispatchShareAction)
                        return true
                    }
                    val songs = IrcShareManager.nowPlayingEntries().take(3)
                    val pickerH = 22f + visibleRows * rowHeight + 6f + 17f + maxOf(1, songs.size) * 17f
                    if (mouseX !in pickerX..(pickerX + pickerW) || mouseY !in pickerY..(pickerY + pickerH)) {
                        isContactPickerOpen = false
                        pendingShareAction = null
                    }
                }

                // Input box click
                val input = inputRect()
                if (input.contains(mouseX, mouseY)) {
                    inputFocused = true
                    cursorTimer = System.currentTimeMillis()
                    if (button == 0) {
                        val sendBtnSize = 16f
                        val sendBtnX = input.x + input.w - sendBtnSize - 5f
                        val sendBtnY = input.y + (input.h - sendBtnSize) * 0.5f
                        if (mouseX in sendBtnX..(sendBtnX + sendBtnSize) && mouseY in sendBtnY..(sendBtnY + sendBtnSize)) {
                            if (sendCurrentInput()) {
                                inputText = ""
                                jumpToBottom()
                            }
                        }
                    }
                    return true
                }
                inputFocused = false

                // Messages area click
                val msgAreaY = subY + subBarHeight + actionBarHeight + 4f
                if (mouseX in x..(x + width) && mouseY in msgAreaY..(msgAreaY + actualMessagesHeight)) {
                    if (button == 0) {
                        val messages = IrcManager.messagesFor(privateTarget ?: IrcManager.channel, direct = privateTarget != null)
                        var msgY = msgAreaY - scrollAmount
                        for (msg in messages) {
                            val imageUrl = IrcImageCache.imageUrl(msg.text)
                            val itemH = messageVisualHeight(msg)
                            if (imageUrl != null && mouseX in (x + pad)..(x + width - pad) && mouseY in (msgY + 24f)..(msgY + 24f + IrcImageCache.previewHeight())) {
                                val now = System.currentTimeMillis()
                                if (doubleClick || (lastImageClickUrl == imageUrl && now - lastImageClickAt < 450L)) {
                                    IrcImageViewerScreen.open(imageUrl)
                                    lastImageClickAt = 0L
                                    lastImageClickUrl = null
                                } else {
                                    lastImageClickAt = now
                                    lastImageClickUrl = imageUrl
                                }
                                return true
                            }
                            msgY += itemH + 3f
                        }
                    }
                    return true
                }
            }
        }

        return false
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int = 0): Boolean {
        val wasDragging = isDragging
        isDragging = false
        return wasDragging
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int = 0, dragX: Float = 0f, dragY: Float = 0f): Boolean {
        if (isDragging) {
            val screenW = ResolutionHelper.scaledWidth
            val screenH = ResolutionHelper.scaledHeight
            x = (mouseX - dragOffsetX).coerceIn(4f, screenW - width - 4f)
            y = (mouseY - dragOffsetY).coerceIn(4f, screenH - headerHeight - 4f)
            savedPosition = Pair(x, y)
            return true
        }
        return false
    }

    fun mouseScrolled(mouseX: Float, mouseY: Float, scrollX: Float = 0f, scrollY: Float = 0f): Boolean {
        if (isMouseOverTotal(mouseX, mouseY) && isExpanded) {
            if (modulePickerOpen) {
                val visible = minOf(8, ModuleManager.modules.size)
                moduleScroll = (moduleScroll - scrollY.toInt()).coerceIn(0, (ModuleManager.modules.size - visible).coerceAtLeast(0))
                return true
            }
            targetScroll -= scrollY * 24f
            pinnedToBottom = false
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                inputFocused = false
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                if (sendCurrentInput()) {
                    inputText = ""
                    jumpToBottom()
                }
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (inputText.isNotEmpty()) {
                    inputText = inputText.substring(0, inputText.length - 1)
                }
                return true
            }
            if ((modifiers and GLFW.GLFW_MOD_CONTROL != 0) && keyCode == GLFW.GLFW_KEY_V) {
                val target = privateTarget ?: IrcManager.channel
                val result = IrcClipboard.paste(target, inputText, 256)
                if (result.imageHandled) {
                    jumpToBottom()
                } else if (result.text != null) {
                    inputText = result.text
                }
                return true
            }
        }
        return false
    }

    fun charTyped(codepoint: Int): Boolean {
        if (inputFocused && codepoint > 0) {
            val char = codepoint.toChar()
            if (!char.isISOControl() && inputText.length < 256) {
                inputText += char
                return true
            }
        }
        return false
    }

    companion object {
        var savedPosition: Pair<Float, Float>? = null
        var savedExpanded: Boolean? = null
        var savedScroll: Float? = null
        var savedOpen: Boolean? = null
    }
}
