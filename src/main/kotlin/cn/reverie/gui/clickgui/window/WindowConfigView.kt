package cn.reverie.gui.clickgui.window
import cn.reverie.app.config.ConfigManager
import cn.reverie.nocturne.IrcShareManager
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import cn.reverie.gui.clickgui.irc.IrcChatModel

internal fun WindowClickGuiScreen.configRowY(): Float =
    WindowClickGuiScreen.SIDEBAR_Y + (cn.reverie.module.Category.entries.size + 1) * WindowClickGuiScreen.SIDEBAR_ROW_STEP

internal fun WindowClickGuiScreen.renderConfigView(mx: Float, my: Float, partialTicks: Float, highlight: NanoVGColor, alpha: Float) {
    val configs = ConfigManager.listConfigs()
    if (selectedConfig == null || !configs.contains(selectedConfig)) {
        selectedConfig = ConfigManager.activeConfig.takeIf { configs.contains(it) } ?: configs.firstOrNull()
    }

    // --- Middle List: Configs ---
    NanoVGRenderer.drawString("CONFIGS", WindowClickGuiScreen.LIST_X, 87f, NanoVGColor(0.88f, 0.89f, 0.92f, alpha), 10f)
    NanoVGRenderer.drawRightVerticallyCenteredString("${configs.size} ITEMS", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W, 89f,
        NanoVGColor(0.48f, 0.49f, 0.53f, alpha), 8f)

    val viewportH = WindowClickGuiScreen.CONTENT_BOTTOM - WindowClickGuiScreen.CONTENT_TOP
    val maxScroll = (configs.size * WindowClickGuiScreen.ROW_STEP - 6f - viewportH).coerceAtLeast(0f)
    configListScrollTarget = configListScrollTarget.coerceIn(0f, maxScroll)
    configListScroll += (configListScrollTarget - configListScroll) * 0.30f
    if (kotlin.math.abs(configListScrollTarget - configListScroll) < 0.08f) configListScroll = configListScrollTarget

    NanoVGRenderer.withScissor(WindowClickGuiScreen.LIST_X, WindowClickGuiScreen.CONTENT_TOP, WindowClickGuiScreen.LIST_W, viewportH) {
        for ((index, name) in configs.withIndex()) {
            val y = WindowClickGuiScreen.CONTENT_TOP + index * WindowClickGuiScreen.ROW_STEP - configListScroll
            if (y + WindowClickGuiScreen.ROW_H < WindowClickGuiScreen.CONTENT_TOP || y > WindowClickGuiScreen.CONTENT_BOTTOM) continue

            val hovered = inside(mx, my, WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H)
            val selected = selectedConfig.equals(name, ignoreCase = true)
            val isActive = name.equals(ConfigManager.activeConfig, ignoreCase = true)

            val rowBg = NanoVGColor(1f, 1f, 1f,
                (0.035f + (if (hovered) 0.035f else 0f) + (if (selected) 0.055f else 0f)) * alpha)
            NanoVGRenderer.drawRoundedRect(WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H, 7f, rowBg)
            NanoVGRenderer.drawRoundedOutline(WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H, 7f,
                NanoVGColor(1f, 1f, 1f, (0.055f + (if (selected) 0.15f else 0f)) * alpha), 0.8f)

            val textColor = (if (isActive) highlight else NanoVGColor(0.78f, 0.79f, 0.82f, alpha))
                .lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), if (selected) 1f else 0f)
            NanoVGRenderer.drawMarqueeString(name, WindowClickGuiScreen.LIST_X + 12f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                160f, hovered, textColor, 10.5f)

            drawSwitch(WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 41f, y + 14f,
                if (isActive) 1f else 0f, if (hovered) 1f else 0f, highlight, alpha)
        }
    }

    if (configs.isEmpty()) {
        NanoVGRenderer.drawCenteredString("No saved configs", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W * 0.5f, 247f,
            NanoVGColor(0.73f, 0.74f, 0.78f, alpha), 11f)
        NanoVGRenderer.drawCenteredString("Create one in the panel on the right", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W * 0.5f, 268f,
            NanoVGColor(0.47f, 0.48f, 0.52f, alpha), 9f)
    }

    drawScrollBar(WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 2f, WindowClickGuiScreen.CONTENT_TOP, viewportH,
        configs.size * WindowClickGuiScreen.ROW_STEP - 6f, configListScroll, alpha)

    // --- Right Details: Selected Config ---
    val cx = WindowClickGuiScreen.DETAIL_X
    val cy = 77f
    val cw = WindowClickGuiScreen.DETAIL_W
    val ch = 393f

    NanoVGRenderer.drawRoundedRect(cx, cy, cw, ch, 9f, NanoVGColor(1f, 1f, 1f, 0.025f * alpha))
    NanoVGRenderer.drawRoundedOutline(cx, cy, cw, ch, 9f, NanoVGColor(1f, 1f, 1f, 0.065f * alpha), 0.8f)

    val currentConfig = selectedConfig
    if (currentConfig != null) {
        val isActive = currentConfig.equals(ConfigManager.activeConfig, ignoreCase = true)
        val headerHovered = inside(mx, my, cx, 84f, cw, 40f)

        NanoVGRenderer.drawMarqueeString(currentConfig, cx + 17f, 103f, 242f, headerHovered,
            NanoVGColor(0.96f, 0.97f, 0.98f, alpha), 16f)

        drawSwitch(cx + cw - 45f, 91f, if (isActive) 1f else 0f, if (headerHovered) 1f else 0f, highlight, alpha)

        val desc = if (isActive) "Currently loaded active profile" else "Saved configuration profile"
        NanoVGRenderer.drawString(desc, cx + 17f, 126f, NanoVGColor(0.59f, 0.60f, 0.64f, alpha), 9f)

        Draw2D.line(cx + 17f, 151f, cx + cw - 17f, 151f, NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 1f)
        NanoVGRenderer.drawString("ACTIONS", cx + 17f, 162f, NanoVGColor(0.83f, 0.84f, 0.87f, alpha), 9f)

        // Actions: Load, Save, Delete, Share
        var btnY = 179f
        val btnH = 26f
        val btnW = cw - 34f
        val bx = cx + 17f

        // 1. Load Button (if not active)
        if (!isActive) {
            val loadHov = inside(mx, my, bx, btnY, btnW, btnH)
            NanoVGRenderer.drawRoundedRect(bx, btnY, btnW, btnH, 5f,
                if (loadHov) highlight.withAlpha(0.24f * alpha) else NanoVGColor(1f, 1f, 1f, 0.045f * alpha))
            NanoVGRenderer.drawRoundedOutline(bx, btnY, btnW, btnH, 5f,
                if (loadHov) highlight.withAlpha(0.6f * alpha) else NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.8f)
            NanoVGRenderer.drawCenteredString("Load Profile", bx + btnW * 0.5f, btnY + btnH * 0.5f + 1f,
                if (loadHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.93f, alpha), 9f)
            btnY += 32f
        }

        // 2. Save / Overwrite Button
        val saveHov = inside(mx, my, bx, btnY, btnW, btnH)
        val saveLabel = if (pendingOverwriteConfig == currentConfig) "Click to confirm overwrite" else "Save Current Settings to This Profile"
        NanoVGRenderer.drawRoundedRect(bx, btnY, btnW, btnH, 5f,
            if (saveHov) highlight.withAlpha(0.24f * alpha) else NanoVGColor(1f, 1f, 1f, 0.045f * alpha))
        NanoVGRenderer.drawRoundedOutline(bx, btnY, btnW, btnH, 5f,
            if (saveHov) highlight.withAlpha(0.6f * alpha) else NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.8f)
        NanoVGRenderer.drawCenteredString(saveLabel, bx + btnW * 0.5f, btnY + btnH * 0.5f + 1f,
            if (saveHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.93f, alpha), 9f)
        btnY += 32f

        // 3. Share to IRC Button
        val shareHov = inside(mx, my, bx, btnY, btnW, btnH)
        NanoVGRenderer.drawRoundedRect(bx, btnY, btnW, btnH, 5f,
            if (shareHov || configSharePickerOpen) highlight.withAlpha(0.20f * alpha) else NanoVGColor(1f, 1f, 1f, 0.045f * alpha))
        NanoVGRenderer.drawRoundedOutline(bx, btnY, btnW, btnH, 5f,
            if (shareHov || configSharePickerOpen) highlight.withAlpha(0.5f * alpha) else NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.8f)
        NanoVGRenderer.drawCenteredString("Share Profile to IRC", bx + btnW * 0.5f, btnY + btnH * 0.5f + 1f,
            if (shareHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.93f, alpha), 9f)
        btnY += 32f

        // 4. Delete Button
        val delHov = inside(mx, my, bx, btnY, btnW, btnH)
        val delLabel = if (pendingDeleteConfig == currentConfig) "Click to confirm deletion" else "Delete Profile"
        val delColor = NanoVGColor(0.95f, 0.40f, 0.40f, alpha)
        NanoVGRenderer.drawRoundedRect(bx, btnY, btnW, btnH, 5f,
            if (delHov) NanoVGColor(0.8f, 0.25f, 0.25f, 0.25f * alpha) else NanoVGColor(1f, 1f, 1f, 0.035f * alpha))
        NanoVGRenderer.drawRoundedOutline(bx, btnY, btnW, btnH, 5f,
            if (delHov) delColor.withAlpha(0.6f) else NanoVGColor(1f, 1f, 1f, 0.08f * alpha), 0.8f)
        NanoVGRenderer.drawCenteredString(delLabel, bx + btnW * 0.5f, btnY + btnH * 0.5f + 1f,
            if (delHov) NanoVGColor.WHITE else delColor, 9f)
        btnY += 36f

        // Divider
        Draw2D.line(cx + 17f, btnY, cx + cw - 17f, btnY, NanoVGColor(1f, 1f, 1f, 0.07f * alpha), 1f)
        btnY += 12f

        // Create New Config Section
        NanoVGRenderer.drawString("CREATE NEW PROFILE", cx + 17f, btnY, NanoVGColor(0.83f, 0.84f, 0.87f, alpha), 9f)
        btnY += 15f

        val inputW = btnW - 68f
        val createBtnW = 62f
        val inputH = 26f
        val inputHov = inside(mx, my, bx, btnY, inputW, inputH)

        NanoVGRenderer.drawRoundedRect(bx, btnY, inputW, inputH, 6f,
            NanoVGColor(0f, 0f, 0f, if (configInputFocused) 0.35f * alpha else 0.22f * alpha))
        NanoVGRenderer.drawRoundedOutline(bx, btnY, inputW, inputH, 6f,
            if (configInputFocused) highlight.withAlpha(0.7f * alpha)
            else if (inputHov) NanoVGColor(1f, 1f, 1f, 0.16f * alpha)
            else NanoVGColor(1f, 1f, 1f, 0.08f * alpha), 0.8f)

        val inputLabel = if (configNameInput.isEmpty() && !configInputFocused) "Profile name..." else configNameInput
        NanoVGRenderer.withScissor(bx + 8f, btnY, inputW - 16f, inputH) {
            NanoVGRenderer.drawVerticallyCenteredString(
                inputLabel, bx + 10f, btnY + inputH * 0.5f,
                if (configNameInput.isEmpty() && !configInputFocused) NanoVGColor(0.48f, 0.50f, 0.55f, 0.8f * alpha)
                else NanoVGColor(0.95f, 0.96f, 0.98f, alpha),
                9f
            )
        }

        if (configInputFocused && (System.currentTimeMillis() - configCursorTimer) / 500L % 2L == 0L) {
            val cursorX = (bx + 10f + NanoVGRenderer.getTextWidth(configNameInput, 9f)).coerceAtMost(bx + inputW - 8f)
            Draw2D.line(cursorX, btnY + 6f, cursorX, btnY + inputH - 6f, highlight.withAlpha(alpha), 1f)
        }

        val createBtnX = bx + inputW + 6f
        val createHov = inside(mx, my, createBtnX, btnY, createBtnW, inputH)
        NanoVGRenderer.drawRoundedRect(createBtnX, btnY, createBtnW, inputH, 6f,
            if (createHov) highlight.withAlpha(0.28f * alpha) else NanoVGColor(1f, 1f, 1f, 0.05f * alpha))
        NanoVGRenderer.drawRoundedOutline(createBtnX, btnY, createBtnW, inputH, 6f,
            if (createHov) highlight.withAlpha(0.6f * alpha) else NanoVGColor(1f, 1f, 1f, 0.10f * alpha), 0.8f)
        NanoVGRenderer.drawCenteredString("Create", createBtnX + createBtnW * 0.5f, btnY + inputH * 0.5f + 1f,
            if (createHov) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.93f, alpha), 8.5f)

        btnY += 34f

        // Status message
        val now = System.currentTimeMillis()
        if (configStatus.isNotEmpty() && now - configStatusAt < 3800L) {
            val statusFade = ((3800L - (now - configStatusAt)) / 600f).coerceIn(0f, 1f)
            val sColor = if (configStatusIsSuccess) NanoVGColor(0.40f, 0.85f, 0.55f, 0.9f * statusFade * alpha)
            else NanoVGColor(0.95f, 0.45f, 0.45f, 0.9f * statusFade * alpha)
            NanoVGRenderer.drawString(configStatus, bx, btnY, sColor, 8.5f)
        }

        // Contact picker for sharing
        if (configSharePickerOpen) {
            val panelX = cx + 10f
            val panelY = 179f
            val panelW = cw - 20f
            val panelH = 279f
            val contacts = IrcChatModel.contacts().take(6)
            val pAlpha = HUDModule.bgOpacity.getFloat() * alpha
            val pBg = NanoVGColor(0.047f, 0.051f, 0.067f, pAlpha)
            if (HUDModule.widgetShadow.value) {
                Draw2D.shadow(panelX, panelY, panelW, panelH, 6f, 12f, NanoVGColor(0f, 0f, 0f, 0.35f * HUDModule.shadowStrength.getFloat() * alpha))
            }
            try {
                RenderEngine.backdropBlurRenderer.renderBackdrop(
                    panelX, panelY, panelW, panelH,
                    radius = 6f,
                    tintColor = pBg,
                    blurRadius = HUDModule.blurRadius.getFloat(),
                    enableBlur = HUDModule.blur.value,
                    backdropOpacity = pAlpha
                )
            } catch (_: Throwable) {
                NanoVGRenderer.drawRoundedRect(panelX, panelY, panelW, panelH, 6f, pBg)
            }
            NanoVGRenderer.drawRoundedOutline(panelX, panelY, panelW, panelH, 6f, NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 0.8f)
            NanoVGRenderer.drawString("CHOOSE AN IRC CONTACT", panelX + 10f, panelY + 11f, NanoVGColor(0.64f, 0.67f, 0.73f, alpha), 8f)
            if (contacts.isEmpty()) {
                NanoVGRenderer.drawCenteredString("No online contacts", panelX + panelW * 0.5f, panelY + 85f,
                    NanoVGColor(0.55f, 0.57f, 0.63f, alpha), 8f)
            }
            contacts.forEachIndexed { index, nick ->
                val rowY = panelY + 28f + index * 30f
                val hovered = inside(mx, my, panelX + 6f, rowY, panelW - 12f, 26f)
                if (hovered) NanoVGRenderer.drawRoundedRect(panelX + 6f, rowY, panelW - 12f, 26f, 4f, NanoVGColor(1f, 1f, 1f, 0.075f * alpha))
                NanoVGRenderer.drawVerticallyCenteredString("@$nick", panelX + 13f, rowY + 13f, NanoVGColor(0.88f, 0.90f, 0.94f, alpha), 8.5f)
                NanoVGRenderer.drawRightVerticallyCenteredString("SEND", panelX + panelW - 13f, rowY + 13f, highlight.withAlpha(alpha), 7.5f)
            }
        }
    }
}

internal fun WindowClickGuiScreen.handleConfigClick(mx: Float, my: Float, button: Int, doubleClick: Boolean = false): Boolean {
    if (button != 0) return false

    val configs = ConfigManager.listConfigs()
    val viewportH = WindowClickGuiScreen.CONTENT_BOTTOM - WindowClickGuiScreen.CONTENT_TOP

    // Middle list clicks
    if (inside(mx, my, WindowClickGuiScreen.LIST_X, WindowClickGuiScreen.CONTENT_TOP, WindowClickGuiScreen.LIST_W, viewportH)) {
        for ((index, name) in configs.withIndex()) {
            val y = WindowClickGuiScreen.CONTENT_TOP + index * WindowClickGuiScreen.ROW_STEP - configListScroll
            if (inside(mx, my, WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H)) {
                selectedConfig = name
                pendingDeleteConfig = null
                pendingOverwriteConfig = null
                val switchX = WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 41f
                val inSwitch = inside(mx, my, switchX, y + 14f, 29f, 15f)
                if (inSwitch || doubleClick) {
                    val ok = ConfigManager.loadConfig(name)
                    setConfigStatus(if (ok) "Loaded config: $name" else "Failed to load: $name", isSuccess = ok)
                }
                return true
            }
        }
        return true
    }

    // Right details clicks
    val cx = WindowClickGuiScreen.DETAIL_X
    val cw = WindowClickGuiScreen.DETAIL_W
    val bx = cx + 17f
    val btnW = cw - 34f
    val currentConfig = selectedConfig ?: return false
    val isActive = currentConfig.equals(ConfigManager.activeConfig, ignoreCase = true)

    if (configSharePickerOpen) {
        val panelX = cx + 10f
        val panelY = 179f
        val panelW = cw - 20f
        val contacts = IrcChatModel.contacts().take(6)
        contacts.forEachIndexed { index, nick ->
            val rowY = panelY + 28f + index * 30f
            if (inside(mx, my, panelX + 6f, rowY, panelW - 12f, 26f)) {
                IrcShareManager.sendCurrentConfig(nick)
                configSharePickerOpen = false
                setConfigStatus("Sent config to @$nick", isSuccess = true)
                return true
            }
        }
        configSharePickerOpen = false
        return true
    }

    // Toggle switch in header
    if (inside(mx, my, cx + cw - 45f, 91f, 30f, 16f)) {
        val ok = ConfigManager.loadConfig(currentConfig)
        setConfigStatus(if (ok) "Loaded config: $currentConfig" else "Failed to load: $currentConfig", isSuccess = ok)
        return true
    }

    var btnY = 179f
    val btnH = 26f

    // 1. Load Button
    if (!isActive) {
        if (inside(mx, my, bx, btnY, btnW, btnH)) {
            val ok = ConfigManager.loadConfig(currentConfig)
            setConfigStatus(if (ok) "Loaded config: $currentConfig" else "Failed to load: $currentConfig", isSuccess = ok)
            return true
        }
        btnY += 32f
    }

    // 2. Save / Overwrite Button
    if (inside(mx, my, bx, btnY, btnW, btnH)) {
        if (pendingOverwriteConfig != currentConfig) {
            pendingOverwriteConfig = currentConfig
            setConfigStatus("Click again to confirm overwrite: $currentConfig", isSuccess = false)
            return true
        }
        val ok = ConfigManager.saveConfig(currentConfig)
        pendingOverwriteConfig = null
        setConfigStatus(if (ok) "Saved config: $currentConfig" else "Failed to save: $currentConfig", isSuccess = ok)
        return true
    }
    btnY += 32f

    // 3. Share to IRC Button
    if (inside(mx, my, bx, btnY, btnW, btnH)) {
        configSharePickerOpen = !configSharePickerOpen
        return true
    }
    btnY += 32f

    // 4. Delete Button
    if (inside(mx, my, bx, btnY, btnW, btnH)) {
        if (pendingDeleteConfig != currentConfig) {
            pendingDeleteConfig = currentConfig
            setConfigStatus("Click again to confirm deletion: $currentConfig", isSuccess = false)
            return true
        }
        val ok = ConfigManager.deleteConfig(currentConfig)
        pendingDeleteConfig = null
        setConfigStatus(if (ok) "Deleted config: $currentConfig" else "Failed to delete: $currentConfig", isSuccess = ok)
        selectedConfig = ConfigManager.activeConfig
        return true
    }
    btnY += 36f + 12f + 15f

    // Create New Profile
    val inputW = btnW - 68f
    val createBtnW = 62f
    val inputH = 26f

    if (inside(mx, my, bx, btnY, inputW, inputH)) {
        configInputFocused = true
        configCursorTimer = System.currentTimeMillis()
        return true
    }

    val createBtnX = bx + inputW + 6f
    if (inside(mx, my, createBtnX, btnY, createBtnW, inputH)) {
        val targetName = configNameInput.trim()
        if (targetName.isEmpty()) {
            setConfigStatus("Enter a profile name", isSuccess = false)
            return true
        }
        if (!ConfigManager.isValidConfigName(targetName)) {
            setConfigStatus("Invalid characters in name", isSuccess = false)
            return true
        }
        val ok = ConfigManager.saveConfig(targetName)
        if (ok) {
            selectedConfig = targetName
            configNameInput = ""
            configInputFocused = false
            setConfigStatus("Created profile: $targetName", isSuccess = true)
        } else {
            setConfigStatus("Failed to create profile", isSuccess = false)
        }
        return true
    }

    configInputFocused = false
    return false
}
