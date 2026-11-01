package cn.reverie.gui.clickgui.window
import cn.reverie.app.config.ConfigManager
import cn.reverie.nocturne.IrcShareManager
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.animation.MotionTracker
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.CategoryPanelChrome
import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.gui.clickgui.ClickGUIModule

/**
 * Dropdown-style Config management panel matching ClickGUI category panel design.
 */
class ConfigProfilePanel(
    initialX: Float = 0f,
    initialY: Float = 46f
) {
    var x: Float = initialX
    var y: Float = initialY

    val width: Float
        get() = ClickGUIModule.panelWidth.getFloat()

    val headerHeight: Float
        get() = ClickGUIModule.headerHeight.getFloat()

    var isOpen: Boolean = savedOpen ?: false
    var isExpanded: Boolean = savedExpanded ?: true

    var isDragging: Boolean = false
        private set
    private var dragOffsetX: Float = 0f
    private var dragOffsetY: Float = 0f

    var scrollAmount: Float = 0f
    var targetScroll: Float = 0f

    var nameInput: String = ""
    var inputFocused: Boolean = false
    private var cursorTimer: Long = 0L

    private var pendingDelete: String? = null
    private var pendingOverwrite: String? = null
    private var status: String = ""
    private var statusAt: Long = 0L
    private var statusIsSuccess: Boolean = true
    private var sharePickerOpen = false

    val expandAnim = FloatAnimation(
        if (isExpanded) 1f else 0f,
        if (isExpanded) 1f else 0f,
        340L,
        CubicBezier.EASE_IN_OUT
    )
    val headerHoverAnim = FloatAnimation(0f, 0f, 200L, CubicBezier.EASE_OUT)
    val dragElevateAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
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

    fun open() {
        isOpen = true
        inputFocused = false
        pendingDelete = null
        pendingOverwrite = null
        status = ""
    }

    fun close() {
        isOpen = false
        inputFocused = false
        isDragging = false
        pendingDelete = null
        pendingOverwrite = null
    }

    fun toggle() {
        if (isOpen) close() else open()
    }

    private fun setStatus(msg: String, isSuccess: Boolean = true) {
        status = msg
        statusAt = System.currentTimeMillis()
        statusIsSuccess = isSuccess
    }

    val contentHeight: Float
        get() {
            val entries = ConfigManager.listConfigs()
            val activeH = 40f
            val createH = 26f
            val labelH = 16f
            val rowsH = entries.size * 34f
            val statusH = if (status.isNotEmpty() && System.currentTimeMillis() - statusAt < 3800L) 18f else 0f
            return activeH + createH + labelH + rowsH + statusH + 12f + if (sharePickerOpen) 82f else 0f
        }

    val maxVisibleContentHeight: Float
        get() {
            val screenH = ResolutionHelper.scaledHeight
            return (screenH - y - headerHeight - 20f).coerceIn(160f, 460f)
        }

    val visibleContentHeight: Float
        get() = minOf(contentHeight, maxVisibleContentHeight)

    val totalHeight: Float
        get() = headerHeight + visibleContentHeight * expandAnim.value

    fun isMouseOverHeader(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + headerHeight
    }

    fun isMouseOverTotal(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + totalHeight
    }

    fun render(mouseX: Float, mouseY: Float, partialTicks: Float = 1f) {
        if (!isOpen) return

        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        expandAnim.durationMs = (240L / animMultiplier).toLong().coerceIn(80L, 600L)
        headerHoverAnim.durationMs = (140L / animMultiplier).toLong().coerceIn(40L, 350L)
        dragElevateAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(50L, 400L)

        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        headerHoverAnim.animateTo(if (isMouseOverHeader(mouseX, mouseY)) 1f else 0f)
        dragElevateAnim.animateTo(if (isDragging) 1f else 0f)

        val currentW = width
        val currentH = totalHeight
        val cornerRadius = ClickGUIModule.cornerRadius.getFloat()

        val maxVisibleH = visibleContentHeight
        val isScrollable = contentHeight > maxVisibleH && expandAnim.value > 0.7f
        val maxScroll = (contentHeight - maxVisibleH).coerceAtLeast(0f)

        targetScroll = targetScroll.coerceIn(0f, maxScroll)
        savedScroll = targetScroll
        if (kotlin.math.abs(targetScroll - scrollAmount) < 0.02f) {
            scrollAmount = targetScroll
        } else {
            scrollAmount += (targetScroll - scrollAmount) * (0.30f * animMultiplier).coerceIn(0.1f, 1f)
        }

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

        val panelAlpha = HUDModule.bgOpacity.getFloat()
        val shadowBlur = 10f + 8f * dragElevateAnim.value
        val shadowAlpha = (0.35f + 0.15f * dragElevateAnim.value) * (panelAlpha / 0.90f).coerceIn(0.3f, 1.2f)
        if (HUDModule.widgetShadow.value) {
            Draw2D.shadow(x, y, currentW, currentH, cornerRadius, shadowBlur, NanoVGColor(0f, 0f, 0f, shadowAlpha * HUDModule.shadowStrength.getFloat()))
        }

        val blurRadius = HUDModule.blurRadius.getFloat()
        val panelBg = NanoVGColor(0.047f, 0.051f, 0.067f, panelAlpha)
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                x, y, currentW, currentH,
                radius = cornerRadius,
                tintColor = panelBg,
                blurRadius = blurRadius,
                enableBlur = HUDModule.blur.value,
                backdropOpacity = panelAlpha
            )
        } catch (_: Throwable) {
            NanoVGRenderer.drawRoundedRect(x, y, currentW, currentH, cornerRadius, panelBg)
        }

        CategoryPanelChrome.draw(x, y, currentW, currentH, headerHeight, cornerRadius, headerHoverAnim.value, expandAnim.value)

        val iconSize = 16f
        val iconX = x + 10f
        val iconY = y + (headerHeight - iconSize) * 0.5f
        val iconColor = NanoVGColor(0.85f, 0.88f, 0.92f, 0.90f).lerp(NanoVGColor.WHITE, headerHoverAnim.value)

        CategoryVectorIcons.drawConfigIcon(iconX + iconSize * 0.5f, iconY + iconSize * 0.5f, iconSize, iconColor)

        val titleX = iconX + iconSize + 8f
        val titleBlur = if (expandAnim.isRunning) kotlin.math.sin(expandAnim.value * Math.PI).toFloat() * 3.8f else 0f

        NanoVGRenderer.drawVerticallyCenteredString(
            "CONFIG",
            titleX,
            y + headerHeight * 0.5f,
            NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f),
            11.5f,
            shadow = HUDModule.textShadow.value,
            blur = titleBlur
        )

        val chevronColor = NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f).lerp(NanoVGColor.WHITE, headerHoverAnim.value * 0.4f)
        CategoryVectorIcons.drawChevronDown(x + currentW - 12f, y + headerHeight * 0.5f, 7f, 1f - expandAnim.value, chevronColor)

        if (expandAnim.value > 0.01f) {
            val contentBoxH = currentH - headerHeight
            val pad = 5f
            val innerX = x + pad
            val innerW = currentW - pad * 2f
            val theme = HUDModule.sampleColor(0f)

            NanoVGRenderer.withScissor(x, y + headerHeight + 1f, currentW, contentBoxH) {
                var curY = y + headerHeight + 5f - scrollAmount

                // 1. Active Profile Card
                NanoVGRenderer.drawRoundedRect(innerX, curY, innerW, 36f, 4f, NanoVGColor(1f, 1f, 1f, 0.025f))
                NanoVGRenderer.drawRoundedOutline(innerX, curY, innerW, 36f, 4f, NanoVGColor(1f, 1f, 1f, 0.06f), 0.75f)

                NanoVGRenderer.drawString("ACTIVE", innerX + 6f, curY + 5f, NanoVGColor(0.52f, 0.55f, 0.62f, 0.9f), 7.5f)
                val activeName = ConfigManager.activeConfig
                NanoVGRenderer.drawRightVerticallyCenteredString(
                    activeName, innerX + innerW - 6f, curY + 9f,
                    theme, 8.5f, shadow = HUDModule.textShadow.value
                )

                val btnW = (innerW - 20f) / 3f
                val btnH = 15f
                val btnY = curY + 17f

                // Save Active button
                val saveActHov = mouseX in (innerX + 6f)..(innerX + 6f + btnW) && mouseY in btnY..(btnY + btnH)
                Draw2D.roundedRect(innerX + 6f, btnY, btnW, btnH, 3f,
                    if (saveActHov) theme.withAlpha(0.28f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                Draw2D.roundedOutline(innerX + 6f, btnY, btnW, btnH, 3f,
                    if (saveActHov) theme else NanoVGColor(1f, 1f, 1f, 0.09f), 0.75f)
                NanoVGRenderer.drawCenteredString("Save", innerX + 6f + btnW * 0.5f, btnY + 7.5f,
                    if (saveActHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.92f, 0.9f), 8f)

                // Reload Active button
                val relX = innerX + 10f + btnW
                val relActHov = mouseX in relX..(relX + btnW) && mouseY in btnY..(btnY + btnH)
                Draw2D.roundedRect(relX, btnY, btnW, btnH, 3f,
                    if (relActHov) theme.withAlpha(0.28f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                Draw2D.roundedOutline(relX, btnY, btnW, btnH, 3f,
                    if (relActHov) theme else NanoVGColor(1f, 1f, 1f, 0.09f), 0.75f)
                NanoVGRenderer.drawCenteredString("Reload", relX + btnW * 0.5f, btnY + 7.5f,
                    if (relActHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.92f, 0.9f), 8f)

                val shareX = relX + btnW + 4f
                val shareHov = mouseX in shareX..(shareX + btnW) && mouseY in btnY..(btnY + btnH)
                Draw2D.roundedRect(shareX, btnY, btnW, btnH, 3f,
                    if (shareHov || sharePickerOpen) theme.withAlpha(0.25f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                Draw2D.roundedOutline(shareX, btnY, btnW, btnH, 3f,
                    if (shareHov || sharePickerOpen) theme else NanoVGColor(1f, 1f, 1f, 0.09f), 0.75f)
                NanoVGRenderer.drawCenteredString("Share", shareX + btnW * 0.5f, btnY + 7.5f,
                    if (shareHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.92f, 0.9f), 7.5f)

                curY += 41f

                if (sharePickerOpen) {
                    val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(4)
                    NanoVGRenderer.drawString("SEND CONFIG TO IRC", innerX + 2f, curY + 1f, NanoVGColor(0.58f, 0.62f, 0.70f, 0.88f), 7f)
                    if (contacts.isEmpty()) NanoVGRenderer.drawString("No online contacts", innerX + 4f, curY + 16f, NanoVGColor(0.55f, 0.57f, 0.63f, 0.8f), 7.5f)
                    contacts.forEachIndexed { index, nick ->
                        val ry = curY + 13f + index * 16f
                        val hover = mouseX in innerX..(innerX + innerW) && mouseY in ry..(ry + 15f)
                        if (hover) NanoVGRenderer.drawRoundedRect(innerX, ry, innerW, 15f, 3f, NanoVGColor(1f, 1f, 1f, 0.07f))
                        NanoVGRenderer.drawVerticallyCenteredString("@$nick", innerX + 5f, ry + 7.5f, NanoVGColor(0.86f, 0.89f, 0.94f, 0.9f), 7.5f)
                    }
                    curY += 78f
                }

                // 2. Create Profile Section
                val saveAsBtnW = 44f
                val inputW = innerW - saveAsBtnW - 4f
                val inputH = 20f
                val inputHov = mouseX in innerX..(innerX + inputW) && mouseY in curY..(curY + inputH)

                NanoVGRenderer.drawRoundedRect(innerX, curY, inputW, inputH, 3.5f,
                    NanoVGColor(0f, 0f, 0f, if (inputFocused) 0.35f else 0.22f))
                NanoVGRenderer.drawRoundedOutline(innerX, curY, inputW, inputH, 3.5f,
                    if (inputFocused) theme.withAlpha(0.7f) else if (inputHov) NanoVGColor(1f, 1f, 1f, 0.16f) else NanoVGColor(1f, 1f, 1f, 0.08f), 0.75f)

                val inputLabel = if (nameInput.isEmpty() && !inputFocused) "New config..." else nameInput
                NanoVGRenderer.withScissor(innerX + 5f, curY, inputW - 10f, inputH) {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        inputLabel, innerX + 6f, curY + inputH * 0.5f,
                        if (nameInput.isEmpty() && !inputFocused) NanoVGColor(0.45f, 0.47f, 0.52f, 0.7f)
                        else NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f),
                        8f
                    )
                }

                if (inputFocused && (System.currentTimeMillis() - cursorTimer) / 500L % 2L == 0L) {
                    val cursorX = (innerX + 6f + NanoVGRenderer.getTextWidth(nameInput, 8f)).coerceAtMost(innerX + inputW - 6f)
                    Draw2D.line(cursorX, curY + 4f, cursorX, curY + inputH - 4f, NanoVGColor(0.95f, 0.95f, 0.98f, 0.9f), 1f)
                }

                val saveAsX = innerX + inputW + 4f
                val saveAsHov = mouseX in saveAsX..(saveAsX + saveAsBtnW) && mouseY in curY..(curY + inputH)
                val saveAsLabel = if (pendingOverwrite == nameInput.trim() && nameInput.isNotBlank()) "Confirm" else "Create"

                Draw2D.roundedRect(saveAsX, curY, saveAsBtnW, inputH, 3.5f,
                    if (saveAsHov) theme.withAlpha(0.35f) else NanoVGColor(1f, 1f, 1f, 0.07f))
                Draw2D.roundedOutline(saveAsX, curY, saveAsBtnW, inputH, 3.5f,
                    if (saveAsHov) theme else NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)
                NanoVGRenderer.drawCenteredString(saveAsLabel, saveAsX + saveAsBtnW * 0.5f, curY + inputH * 0.5f,
                    if (saveAsHov) NanoVGColor.WHITE else NanoVGColor(0.90f, 0.92f, 0.96f, 0.9f), 8f)

                curY += 25f

                // 3. Saved Profiles Header
                val entries = ConfigManager.listConfigs()
                NanoVGRenderer.drawString("SAVED PROFILES (${entries.size})", innerX + 2f, curY + 2f, NanoVGColor(0.50f, 0.53f, 0.60f, 0.85f), 7.5f)
                curY += 13f

                if (entries.isEmpty()) {
                    NanoVGRenderer.drawVerticallyCenteredString("No configs found", innerX + 4f, curY + 8f, NanoVGColor(0.45f, 0.47f, 0.52f, 0.7f), 8f)
                    curY += 18f
                } else {
                    for (name in entries) {
                        val rowH = 30f
                        val isRowActive = name == ConfigManager.activeConfig
                        val rowHov = mouseX in innerX..(innerX + innerW) && mouseY in curY..(curY + rowH)

                        val bgAlpha = if (isRowActive) 0.05f else if (rowHov) 0.035f else 0.015f
                        NanoVGRenderer.drawRoundedRect(innerX, curY, innerW, rowH, 3.5f,
                            if (isRowActive) theme.withAlpha(0.12f) else NanoVGColor(1f, 1f, 1f, bgAlpha))
                        NanoVGRenderer.drawRoundedOutline(innerX, curY, innerW, rowH, 3.5f,
                            if (isRowActive) theme.withAlpha(0.35f) else NanoVGColor(1f, 1f, 1f, 0.05f), 0.75f)

                        // Name & active tag
                        val nameColor = if (isRowActive) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.92f, 0.9f)
                        NanoVGRenderer.drawVerticallyCenteredString(name, innerX + 6f, curY + 10f, nameColor, 8.5f, shadow = HUDModule.textShadow.value)
                        if (isRowActive) {
                            NanoVGRenderer.drawRightVerticallyCenteredString("LOADED", innerX + innerW - 6f, curY + 10f, theme, 7f)
                        }

                        // Action Buttons: Load, Save, Del
                        val actBtnH = 12f
                        val actBtnY = curY + 16f
                        val loadW = 32f
                        val saveW = if (pendingOverwrite == name) 38f else 32f
                        val delW = if (pendingDelete == name) 38f else 28f

                        var actX = innerX + 6f

                        // Load
                        val loadHov = mouseX in actX..(actX + loadW) && mouseY in actBtnY..(actBtnY + actBtnH)
                        Draw2D.roundedRect(actX, actBtnY, loadW, actBtnH, 2.5f,
                            if (loadHov) theme.withAlpha(0.35f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                        NanoVGRenderer.drawCenteredString("Load", actX + loadW * 0.5f, actBtnY + actBtnH * 0.5f,
                            if (loadHov) NanoVGColor.WHITE else NanoVGColor(0.80f, 0.83f, 0.88f, 0.85f), 7.5f)
                        actX += loadW + 4f

                        // Save
                        val saveHov = mouseX in actX..(actX + saveW) && mouseY in actBtnY..(actBtnY + actBtnH)
                        Draw2D.roundedRect(actX, actBtnY, saveW, actBtnH, 2.5f,
                            if (saveHov) theme.withAlpha(0.35f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                        NanoVGRenderer.drawCenteredString(if (pendingOverwrite == name) "Sure?" else "Save",
                            actX + saveW * 0.5f, actBtnY + actBtnH * 0.5f,
                            if (saveHov) NanoVGColor.WHITE else NanoVGColor(0.80f, 0.83f, 0.88f, 0.85f), 7.5f)
                        actX += saveW + 4f

                        // Del
                        val delHov = mouseX in actX..(actX + delW) && mouseY in actBtnY..(actBtnY + actBtnH)
                        Draw2D.roundedRect(actX, actBtnY, delW, actBtnH, 2.5f,
                            if (delHov) NanoVGColor(0.95f, 0.35f, 0.35f, 0.40f) else NanoVGColor(1f, 1f, 1f, 0.05f))
                        NanoVGRenderer.drawCenteredString(if (pendingDelete == name) "Sure?" else "Del",
                            actX + delW * 0.5f, actBtnY + actBtnH * 0.5f,
                            if (delHov) NanoVGColor(1f, 0.6f, 0.6f, 1f) else NanoVGColor(0.70f, 0.72f, 0.76f, 0.80f), 7.5f)

                        curY += 34f
                    }
                }

                // 4. Status Notification
                val now = System.currentTimeMillis()
                if (status.isNotEmpty() && now - statusAt < 3800L) {
                    val statusFade = ((3800L - (now - statusAt)) / 600f).coerceIn(0f, 1f)
                    val sColor = if (statusIsSuccess) NanoVGColor(0.40f, 0.85f, 0.55f, 0.9f * statusFade)
                    else NanoVGColor(0.95f, 0.45f, 0.45f, 0.9f * statusFade)
                    NanoVGRenderer.drawVerticallyCenteredString(status, innerX + 4f, curY + 6f, sColor, 7.5f)
                }
            }

            // Scrollbar
            if (isScrollable) {
                val trackX = x + currentW - 3.5f
                val trackY = y + headerHeight + 4f
                val trackH = contentBoxH - 8f

                val thumbH = (maxVisibleH / contentHeight * trackH).coerceAtLeast(16f)
                val thumbY = trackY + (scrollAmount / maxScroll).coerceIn(0f, 1f) * (trackH - thumbH)

                NanoVGRenderer.drawRoundedRect(trackX, trackY, 1.5f, trackH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.05f))
                NanoVGRenderer.drawRoundedRect(trackX, thumbY, 1.5f, thumbH, 0.75f, NanoVGColor(1f, 1f, 1f, 0.25f))
            }
        }
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (!isOpen) return false

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
                val pad = 5f
                val innerX = x + pad
                val innerW = width - pad * 2f
                var curY = y + headerHeight + 5f - scrollAmount

                // Active section clicks
                val btnW = (innerW - 20f) / 3f
                val btnH = 15f
                val btnY = curY + 17f

                // Save active
                if (button == 0 && mouseX in (innerX + 6f)..(innerX + 6f + btnW) && mouseY in btnY..(btnY + btnH)) {
                    val active = ConfigManager.activeConfig
                    val ok = ConfigManager.saveConfig(active)
                    setStatus(if (ok) "Saved config: $active" else "Failed to save: $active", isSuccess = ok)
                    return true
                }

                // Reload active
                val relX = innerX + 10f + btnW
                if (button == 0 && mouseX in relX..(relX + btnW) && mouseY in btnY..(btnY + btnH)) {
                    val active = ConfigManager.activeConfig
                    val ok = ConfigManager.loadConfig(active)
                    setStatus(if (ok) "Reloaded config: $active" else "Failed to reload: $active", isSuccess = ok)
                    return true
                }

                val shareX = relX + btnW + 4f
                if (button == 0 && mouseX in shareX..(shareX + btnW) && mouseY in btnY..(btnY + btnH)) {
                    sharePickerOpen = !sharePickerOpen
                    return true
                }

                curY += 41f

                if (sharePickerOpen) {
                    val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(4)
                    for ((index, nick) in contacts.withIndex()) {
                        val ry = curY + 13f + index * 16f
                        if (button == 0 && mouseX in innerX..(innerX + innerW) && mouseY in ry..(ry + 15f)) {
                            IrcShareManager.sendCurrentConfig(nick)
                            sharePickerOpen = false
                            return true
                        }
                    }
                    curY += 78f
                }

                // Create section
                val saveAsBtnW = 44f
                val inputW = innerW - saveAsBtnW - 4f
                val inputH = 20f

                if (button == 0 && mouseX in innerX..(innerX + inputW) && mouseY in curY..(curY + inputH)) {
                    inputFocused = true
                    cursorTimer = System.currentTimeMillis()
                    return true
                }

                val saveAsX = innerX + inputW + 4f
                if (button == 0 && mouseX in saveAsX..(saveAsX + saveAsBtnW) && mouseY in curY..(curY + inputH)) {
                    val targetName = nameInput.trim()
                    if (targetName.isEmpty()) {
                        setStatus("Enter a config name.", isSuccess = false)
                        return true
                    }
                    if (!ConfigManager.isValidConfigName(targetName)) {
                        setStatus("Name only supports letters, numbers, spaces, _ or - (max 32).", isSuccess = false)
                        return true
                    }
                    if (ConfigManager.listConfigs().any { it.equals(targetName, ignoreCase = true) } && pendingOverwrite != targetName) {
                        pendingOverwrite = targetName
                        setStatus("Click again to confirm overwrite: $targetName", isSuccess = false)
                        return true
                    }
                    val ok = ConfigManager.saveConfig(targetName)
                    pendingOverwrite = null
                    setStatus(if (ok) "Created config: $targetName" else "Failed to save: $targetName", isSuccess = ok)
                    if (ok) {
                        nameInput = ""
                        inputFocused = false
                    }
                    return true
                }

                curY += 25f + 13f

                val entries = ConfigManager.listConfigs()
                for (name in entries) {
                    val rowH = 30f
                    if (mouseY in curY..(curY + rowH)) {
                        val actBtnH = 12f
                        val actBtnY = curY + 16f
                        val loadW = 32f
                        val saveW = if (pendingOverwrite == name) 38f else 32f
                        val delW = if (pendingDelete == name) 38f else 28f

                        var actX = innerX + 6f

                        // Load
                        if (button == 0 && mouseX in actX..(actX + loadW) && mouseY in actBtnY..(actBtnY + actBtnH)) {
                            val ok = ConfigManager.loadConfig(name)
                            setStatus(if (ok) "Loaded config: $name" else "Failed to load: $name", isSuccess = ok)
                            return true
                        }
                        actX += loadW + 4f

                        // Save / Overwrite
                        if (button == 0 && mouseX in actX..(actX + saveW) && mouseY in actBtnY..(actBtnY + actBtnH)) {
                            if (pendingOverwrite != name) {
                                pendingOverwrite = name
                                setStatus("Click again to confirm overwrite: $name", isSuccess = false)
                                return true
                            }
                            val ok = ConfigManager.saveConfig(name)
                            pendingOverwrite = null
                            setStatus(if (ok) "Saved config: $name" else "Failed to save: $name", isSuccess = ok)
                            return true
                        }
                        actX += saveW + 4f

                        // Delete
                        if (button == 0 && mouseX in actX..(actX + delW) && mouseY in actBtnY..(actBtnY + actBtnH)) {
                            if (pendingDelete != name) {
                                pendingDelete = name
                                setStatus(if (name == ConfigManager.activeConfig) "Confirm delete active config (switches to default)?" else "Click again to confirm delete: $name", isSuccess = false)
                                return true
                            }
                            val ok = ConfigManager.deleteConfig(name)
                            pendingDelete = null
                            setStatus(if (ok) "Deleted config: $name" else "Failed to delete: $name", isSuccess = ok)
                            return true
                        }
                    }
                    curY += 34f
                }

                inputFocused = false
                return true
            }
        }

        inputFocused = false
        return false
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int = 0): Boolean {
        if (!isOpen) return false
        val wasDragging = isDragging
        isDragging = false
        return wasDragging
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int = 0, dragX: Float = 0f, dragY: Float = 0f): Boolean {
        if (!isOpen) return false
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
        if (!isOpen) return false
        if (isMouseOverTotal(mouseX, mouseY) && isExpanded) {
            targetScroll -= scrollY * 24f
            return true
        }
        return false
    }

    fun keyPressed(keyCode: Int, scanCodeOrModifiers: Int = 0, modifiers: Int = 0): Boolean {
        if (!isOpen) return false
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
                if (keyCode == GLFW.GLFW_KEY_ENTER && nameInput.isNotBlank()) {
                    val targetName = nameInput.trim()
                    if (ConfigManager.isValidConfigName(targetName)) {
                        val ok = ConfigManager.saveConfig(targetName)
                        setStatus(if (ok) "Created config: $targetName" else "Failed to save: $targetName", isSuccess = ok)
                        if (ok) nameInput = ""
                    } else {
                        setStatus("Invalid config name.", isSuccess = false)
                    }
                }
                inputFocused = false
                return true
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (nameInput.isNotEmpty()) {
                    nameInput = nameInput.substring(0, nameInput.length - 1)
                }
                return true
            }
        }
        return false
    }

    fun charTyped(codepoint: Int): Boolean {
        if (!isOpen) return false
        if (inputFocused && codepoint > 0) {
            val char = codepoint.toChar()
            if (!char.isISOControl() && nameInput.length < 32) {
                nameInput += char
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
