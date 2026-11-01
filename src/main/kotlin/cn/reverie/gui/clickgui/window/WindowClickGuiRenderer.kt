package cn.reverie.gui.clickgui.window
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.gui.clickgui.ClickGUIModule
import cn.reverie.gui.clickgui.ClickGuiSearchFieldLayout
import cn.reverie.gui.clickgui.irc.ircRowY
/** A single, movable glass workspace. The existing setting components remain the source of truth. */

internal fun WindowClickGuiScreen.renderHeader(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float) {
    val comboActive = logoClickCount > 0 && System.currentTimeMillis() - lastLogoClickTime < 1600L
    val moonColor = if (comboActive) {
        val clickRatio = (logoClickCount / 5f).coerceIn(0f, 1f)
        NanoVGColor.WHITE.lerp(highlight, clickRatio)
    } else {
        NanoVGColor(0.94f, 0.95f, 0.97f, alpha)
    }
    HudVectorIcons.drawMoon(20f, 21.5f, 13f, moonColor)
    NanoVGRenderer.drawString("NOCTURNE", 37f, 23f, if (comboActive) highlight.withAlpha(alpha) else NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 12f)

    val searchX = 183f
    val searchHovered = searchField.bounds.contains(mx, my)
    searchFocusAnim.animateTo(if (searchFocused || searchHovered) 1f else 0f)
    val searchW = 330f + 12f * searchFocusAnim.value
    searchField = ClickGuiSearchFieldLayout(Rect2f(searchX, 16f, searchW, 29f))
    NanoVGRenderer.drawRoundedRect(searchX, 16f, searchW, 29f, 7f,
        NanoVGColor(0f, 0f, 0f, (0.22f + searchFocusAnim.value * 0.10f) * alpha))
    NanoVGRenderer.drawRoundedOutline(searchX, 16f, searchW, 29f, 7f,
        NanoVGColor(1f, 1f, 1f, (0.10f + searchFocusAnim.value * 0.12f) * alpha), 1f)
    CategoryVectorIcons.drawSearchIcon(searchX + 11f, 25f, 10f, NanoVGColor(0.62f, 0.64f, 0.68f, alpha))
    val searchLabel = if (search.isEmpty() && !searchFocused) "Search..." else search
    val text = searchField.text
    val textW = NanoVGRenderer.getTextWidth(search, 10f)
    val textOffset = if (searchFocused) (textW - text.width + 2f).coerceAtLeast(0f) else 0f
    NanoVGRenderer.withScissor(text.x, text.y, text.width, text.height) {
        NanoVGRenderer.drawVerticallyCenteredString(searchLabel, text.x - textOffset, 30.5f,
            if (search.isEmpty()) NanoVGColor(0.50f, 0.51f, 0.55f, alpha) else NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 10f)
        if (searchFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
            val cursorX = (text.x + textW - textOffset + 1f).coerceAtMost(text.x2 - 1.5f)
            Draw2D.line(cursorX, 23f, cursorX, 38f, highlight.withAlpha(alpha), 1f)
        }
    }
    if (search.isNotEmpty()) {
        val clear = searchField.clearButton
        CategoryVectorIcons.drawClearIcon(clear.centerX - 4f, clear.centerY - 4f, 8f,
            if (clear.contains(mx, my)) NanoVGColor.WHITE.withAlpha(alpha) else NanoVGColor(0.78f, 0.79f, 0.82f, alpha))
    }

    closeHoverAnim.animateTo(if (inside(mx, my, 755f, 17f, 22f, 27f)) 1f else 0f)
    NanoVGRenderer.drawRoundedRect(755f, 17f, 22f, 27f, 6f,
        NanoVGColor(1f, 1f, 1f, (0.06f + closeHoverAnim.value * 0.10f) * alpha))
    NanoVGRenderer.drawCenteredString("×", 766f, 30.5f,
        NanoVGColor(0.78f + closeHoverAnim.value * 0.18f, 0.79f + closeHoverAnim.value * 0.18f, 0.82f + closeHoverAnim.value * 0.18f, alpha), 17f)
}

internal fun WindowClickGuiScreen.renderSidebar(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float) {
    val categories = listOf<Category?>(null) + Category.entries
    val rowH = WindowClickGuiScreen.SIDEBAR_ROW_H
    val rowCenter = rowH * 0.5f
    var y = WindowClickGuiScreen.SIDEBAR_Y
    for (category in categories) {
        val hovered = inside(mx, my, 11f, y, 128f, rowH)
        val hoverAnim = categoryHoverMotions.getValue(category)
        val selectAnim = categorySelectMotions.getValue(category)
        hoverAnim.animateTo(if (hovered) 1f else 0f)
        selectAnim.animateTo(if (selectedCategory == category && !ircSelected && !configSelected) 1f else 0f)
        val hover = hoverAnim.value
        val selected = selectAnim.value
        val emphasis = maxOf(hover, selected)
        if (emphasis > 0.01f) NanoVGRenderer.drawRoundedRect(10f, y, 129f, rowH, 5f,
            NanoVGColor(1f, 1f, 1f, (0.05f * hover + 0.09f * selected) * alpha))
        if (category == null) {
            CategoryVectorIcons.drawAllIcon(30f, y + rowCenter, 14f,
                NanoVGColor(0.64f, 0.65f, 0.69f, alpha).lerp(highlight.withAlpha(alpha), selected), emphasis)
        } else {
            CategoryVectorIcons.drawCategoryIcon(category, 23.5f, y + rowCenter - 7f, 14f,
                NanoVGColor(0.65f, 0.66f, 0.70f, alpha).lerp(highlight.withAlpha(alpha), selected))
        }
        NanoVGRenderer.drawVerticallyCenteredString(category?.displayName ?: "All modules", 47f, y + rowCenter,
            NanoVGColor(0.70f, 0.71f, 0.75f, alpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), selected), 9.5f)
        y += WindowClickGuiScreen.SIDEBAR_ROW_STEP
    }

    // Config Tab
    val cfgY = configRowY()
    val cfgRowHovered = inside(mx, my, 11f, cfgY, 128f, rowH)
    configHoverAnim.animateTo(if (cfgRowHovered) 1f else 0f)
    configSelectAnim.animateTo(if (configSelected) 1f else 0f)
    val cfgEmphasis = maxOf(configHoverAnim.value, configSelectAnim.value)
    if (cfgEmphasis > 0.01f) NanoVGRenderer.drawRoundedRect(10f, cfgY, 129f, rowH, 5f,
        NanoVGColor(1f, 1f, 1f, (0.05f * configHoverAnim.value + 0.09f * configSelectAnim.value) * alpha))
    CategoryVectorIcons.drawConfigIcon(30f, cfgY + rowCenter, 14f,
        NanoVGColor(0.64f, 0.65f, 0.69f, alpha).lerp(highlight.withAlpha(alpha), configSelectAnim.value))
    NanoVGRenderer.drawVerticallyCenteredString("Config", 47f, cfgY + rowCenter,
        NanoVGColor(0.70f, 0.71f, 0.75f, alpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), configSelectAnim.value), 9.5f)

    // IRC Tab
    val ircY = ircRowY()
    val ircRowHovered = inside(mx, my, 11f, ircY, 128f, rowH)
    ircHoverAnim.animateTo(if (ircRowHovered) 1f else 0f)
    ircSelectAnim.animateTo(if (ircSelected) 1f else 0f)
    val ircEmphasis = maxOf(ircHoverAnim.value, ircSelectAnim.value)
    if (ircEmphasis > 0.01f) NanoVGRenderer.drawRoundedRect(10f, ircY, 129f, rowH, 5f,
        NanoVGColor(1f, 1f, 1f, (0.05f * ircHoverAnim.value + 0.09f * ircSelectAnim.value) * alpha))
    CategoryVectorIcons.drawChatIcon(30f, ircY + rowCenter, 14f,
        NanoVGColor(0.64f, 0.65f, 0.69f, alpha).lerp(highlight.withAlpha(alpha), ircSelectAnim.value))
    NanoVGRenderer.drawVerticallyCenteredString("IRC", 47f, ircY + rowCenter,
        NanoVGColor(0.70f, 0.71f, 0.75f, alpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), ircSelectAnim.value), 9.5f)
}

internal fun WindowClickGuiScreen.renderList(mx: Float, my: Float, modules: List<Module>, highlight: NanoVGColor, alpha: Float) {
    NanoVGRenderer.drawString(if (search.isNotBlank()) "RESULTS" else
        (selectedCategory?.displayName?.uppercase() ?: "ALL MODULES"), WindowClickGuiScreen.LIST_X, 87f,
        NanoVGColor(0.88f, 0.89f, 0.92f, alpha), 10f)
    NanoVGRenderer.drawRightVerticallyCenteredString("${modules.size} ITEMS", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W, 89f,
        NanoVGColor(0.48f, 0.49f, 0.53f, alpha), 8f)
    val viewportH = WindowClickGuiScreen.CONTENT_BOTTOM - WindowClickGuiScreen.CONTENT_TOP
    val maxScroll = (modules.size * WindowClickGuiScreen.ROW_STEP - 6f - viewportH).coerceAtLeast(0f)
    listScrollTarget = listScrollTarget.coerceIn(0f, maxScroll)
    listScroll += (listScrollTarget - listScroll) * 0.30f
    if (kotlin.math.abs(listScrollTarget - listScroll) < 0.08f) listScroll = listScrollTarget
    NanoVGRenderer.withScissor(WindowClickGuiScreen.LIST_X, WindowClickGuiScreen.CONTENT_TOP, WindowClickGuiScreen.LIST_W, viewportH) {
        for ((index, module) in modules.withIndex()) {
            val entry = rowEntryProgress(index)
            val y = rowY(index)
            if (y + WindowClickGuiScreen.ROW_H < WindowClickGuiScreen.CONTENT_TOP || y > WindowClickGuiScreen.CONTENT_BOTTOM) continue
            val hovered = inside(mx, my, WindowClickGuiScreen.LIST_X, y, WindowClickGuiScreen.LIST_W, WindowClickGuiScreen.ROW_H)
            val motion = rowMotions.getValue(module)
            motion.hover.animateTo(if (hovered) 1f else 0f)
            motion.selected.animateTo(if (selectedModule == module) 1f else 0f)
            motion.enabled.animateTo(if (module.enabled) 1f else 0f)
            val hover = motion.hover.value
            val selected = motion.selected.value
            val visualX = WindowClickGuiScreen.LIST_X + (1f - entry) * 8f - hover * 1.5f
            val visualW = WindowClickGuiScreen.LIST_W - (1f - entry) * 8f
            val rowAlpha = alpha * entry
            val rowBg = NanoVGColor(1f, 1f, 1f,
                (0.035f + hover * 0.035f + selected * 0.055f) * rowAlpha)
            NanoVGRenderer.drawRoundedRect(visualX, y, visualW, WindowClickGuiScreen.ROW_H, 7f, rowBg)
            NanoVGRenderer.drawRoundedOutline(visualX, y, visualW, WindowClickGuiScreen.ROW_H, 7f,
                NanoVGColor(1f, 1f, 1f, (0.055f + selected * 0.15f) * rowAlpha), 0.8f)
            NanoVGRenderer.drawMarqueeString(module.name, visualX + 12f, y + WindowClickGuiScreen.ROW_H * 0.5f,
                190f, hovered, NanoVGColor(0.78f, 0.79f, 0.82f, rowAlpha)
                    .lerp(NanoVGColor(0.97f, 0.98f, 1f, rowAlpha), selected), 10.5f)
            drawSwitch(visualX + visualW - 41f, y + 14f, motion.enabled.value, hover, highlight, rowAlpha)
        }
    }
    if (modules.isEmpty()) {
        NanoVGRenderer.drawCenteredString("No matching modules", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W * 0.5f, 247f,
            NanoVGColor(0.73f, 0.74f, 0.78f, alpha), 11f)
        NanoVGRenderer.drawCenteredString("Try another category or search", WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W * 0.5f, 268f,
            NanoVGColor(0.47f, 0.48f, 0.52f, alpha), 9f)
    }
    drawScrollBar(WindowClickGuiScreen.LIST_X + WindowClickGuiScreen.LIST_W - 2f, WindowClickGuiScreen.CONTENT_TOP, viewportH, modules.size * WindowClickGuiScreen.ROW_STEP - 6f, listScroll, alpha)
}

internal fun WindowClickGuiScreen.renderDetails(mx: Float, my: Float, partialTicks: Float, highlight: NanoVGColor, alpha: Float) {
    NanoVGRenderer.drawRoundedRect(WindowClickGuiScreen.DETAIL_X, 77f, WindowClickGuiScreen.DETAIL_W, 393f, 9f, NanoVGColor(1f, 1f, 1f, 0.025f * alpha))
    NanoVGRenderer.drawRoundedOutline(WindowClickGuiScreen.DETAIL_X, 77f, WindowClickGuiScreen.DETAIL_W, 393f, 9f,
        NanoVGColor(1f, 1f, 1f, 0.065f * alpha), 0.8f)
    val module = selectedModule
    if (module == null) {
        NanoVGRenderer.drawCenteredString("Select a module", WindowClickGuiScreen.DETAIL_X + WindowClickGuiScreen.DETAIL_W * 0.5f, 248f,
            NanoVGColor(0.69f, 0.70f, 0.74f, alpha), 11f)
        return
    }
    detailTransitionAnim.animateTo(1f)
    val detailProgress = detailTransitionAnim.value
    detailContentOffsetX = (1f - detailProgress) * 10f
    val detailMouseX = mx - detailContentOffsetX
    val detailFade = 0.55f + 0.45f * detailProgress
    val transform = RenderEngine.nanoVG.transform
    transform.save()
    transform.translate(detailContentOffsetX, 0f)
    val nvg = RenderEngine.nanoVG.handle
    org.lwjgl.nanovg.NanoVG.nvgSave(nvg)
    org.lwjgl.nanovg.NanoVG.nvgGlobalAlpha(nvg, detailFade)
    try {
        NanoVGRenderer.drawMarqueeString(module.name, WindowClickGuiScreen.DETAIL_X + 17f, 103f, 242f,
            inside(detailMouseX, my, WindowClickGuiScreen.DETAIL_X, 84f, WindowClickGuiScreen.DETAIL_W, 40f), NanoVGColor(0.96f, 0.97f, 0.98f, alpha), 16f)
        val motion = rowMotions.getValue(module)
        motion.enabled.animateTo(if (module.enabled) 1f else 0f)
        drawSwitch(WindowClickGuiScreen.DETAIL_X + WindowClickGuiScreen.DETAIL_W - 45f, 91f, motion.enabled.value, motion.hover.value, highlight, alpha)
        NanoVGRenderer.withScissor(WindowClickGuiScreen.DETAIL_X + 17f, 119f, WindowClickGuiScreen.DETAIL_W - 34f, 24f) {
            NanoVGRenderer.drawMarqueeString(module.description, WindowClickGuiScreen.DETAIL_X + 17f, 131f,
                WindowClickGuiScreen.DETAIL_W - 34f, inside(detailMouseX, my, WindowClickGuiScreen.DETAIL_X, 119f, WindowClickGuiScreen.DETAIL_W, 24f),
                NanoVGColor(0.59f, 0.60f, 0.64f, alpha), 9f)
        }
        Draw2D.line(WindowClickGuiScreen.DETAIL_X + 17f, 151f, WindowClickGuiScreen.DETAIL_X + WindowClickGuiScreen.DETAIL_W - 17f, 151f,
            NanoVGColor(1f, 1f, 1f, 0.09f * alpha), 1f)
        NanoVGRenderer.drawString("SETTINGS", WindowClickGuiScreen.DETAIL_X + 17f, 162f, NanoVGColor(0.83f, 0.84f, 0.87f, alpha), 9f)

        val shareX = WindowClickGuiScreen.DETAIL_X + WindowClickGuiScreen.DETAIL_W - 137f
        val shareHover = inside(detailMouseX, my, shareX, 155f, 126f, 19f)
        NanoVGRenderer.drawRoundedRect(shareX, 155f, 126f, 19f, 4f,
            NanoVGColor(1f, 1f, 1f, (if (shareHover || moduleSharePickerOpen) 0.11f else 0.045f) * alpha))
        NanoVGRenderer.drawRoundedOutline(shareX, 155f, 126f, 19f, 4f, NanoVGColor(1f, 1f, 1f, 0.10f * alpha), 0.75f)
        NanoVGRenderer.drawCenteredString("Share module config", shareX + 63f, 164.5f, NanoVGColor(0.85f, 0.88f, 0.93f, alpha), 7.5f)
        if (moduleSharePickerOpen) {
            val panelX = WindowClickGuiScreen.DETAIL_X + 10f
            val panelY = 179f
            val panelW = WindowClickGuiScreen.DETAIL_W - 20f
            val panelH = 279f
            val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(6)
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
            NanoVGRenderer.drawString("CHOOSE AN IRC CONTACT", panelX + 10f, panelY + 9f, NanoVGColor(0.64f, 0.67f, 0.73f, alpha), 8f)
            if (contacts.isEmpty()) NanoVGRenderer.drawCenteredString("No online contacts", panelX + panelW * 0.5f, panelY + 85f,
                NanoVGColor(0.55f, 0.57f, 0.63f, alpha), 8f)
            contacts.forEachIndexed { index, nick ->
                val rowY = 205f + index * 30f
                val hovered = inside(detailMouseX, my, panelX + 6f, rowY, panelW - 12f, 26f)
                if (hovered) NanoVGRenderer.drawRoundedRect(panelX + 6f, rowY, panelW - 12f, 26f, 4f, NanoVGColor(1f, 1f, 1f, 0.075f * alpha))
                NanoVGRenderer.drawVerticallyCenteredString("@$nick", panelX + 13f, rowY + 13f, NanoVGColor(0.88f, 0.90f, 0.94f, alpha), 8f)
                NanoVGRenderer.drawRightVerticallyCenteredString("SEND", panelX + panelW - 13f, rowY + 13f, NanoVGColor(0.63f, 0.76f, 0.98f, alpha), 7f)
            }
            return
        }
        val components = buttons[module]?.settingComponents ?: return
        val top = 179f
        val bottom = 458f
        val viewportH = bottom - top
        val animSpeed = ClickGUIModule.animSpeed.getFloat()
        for (comp in components) {
            comp.width = WindowClickGuiScreen.DETAIL_W - 24f
            comp.updateVisibilityAnim(animSpeed)
        }
        val contentH = components.sumOf { (it.height * it.visibilityAnim.value + 5f * it.visibilityAnim.value).toDouble() }.toFloat()
        val maxScroll = (contentH - viewportH).coerceAtLeast(0f)
        detailScrollTarget = detailScrollTarget.coerceIn(0f, maxScroll)
        detailScroll += (detailScrollTarget - detailScroll) * 0.30f
        if (kotlin.math.abs(detailScrollTarget - detailScroll) < 0.08f) detailScroll = detailScrollTarget
        var componentY = top - detailScroll
        NanoVGRenderer.withScissor(WindowClickGuiScreen.DETAIL_X + 10f, top, WindowClickGuiScreen.DETAIL_W - 20f, viewportH) {
            for (comp in components) {
                val visibleHeight = comp.height * comp.visibilityAnim.value
                comp.x = WindowClickGuiScreen.DETAIL_X + 12f
                comp.y = componentY
                if (visibleHeight > 0.5f && componentY + visibleHeight >= top && componentY <= bottom) {
                    comp.render(detailMouseX, my, partialTicks)
                }
                componentY += visibleHeight + 5f * comp.visibilityAnim.value
            }
        }
        drawScrollBar(WindowClickGuiScreen.DETAIL_X + WindowClickGuiScreen.DETAIL_W - 7f, top, viewportH, contentH, detailScroll, alpha)
    } finally {
        org.lwjgl.nanovg.NanoVG.nvgRestore(nvg)
        transform.restore()
    }
}

internal fun WindowClickGuiScreen.drawSwitch(x: Float, y: Float, enabled: Float, hover: Float, highlight: NanoVGColor, alpha: Float) {
    val trackColor = NanoVGColor(0.32f, 0.33f, 0.36f, (0.58f + hover * 0.12f) * alpha)
        .lerp(NanoVGColor(0.70f, 0.71f, 0.74f, 0.86f * alpha), enabled)
    NanoVGRenderer.drawRoundedRect(x, y, 29f + hover, 15f, 7.5f, trackColor)
    val knobX = x + 8f + 13f * enabled + hover * enabled
    val knobColor = highlight.withAlpha(alpha).lerp(NanoVGColor(0.12f, 0.12f, 0.13f, alpha), enabled)
    NanoVGRenderer.drawCircle(knobX, y + 7.5f, 5.2f + hover * 0.25f, knobColor)
}

internal fun WindowClickGuiScreen.drawScrollBar(x: Float, y: Float, viewportH: Float, contentH: Float, scroll: Float, alpha: Float) {
    if (contentH <= viewportH) return
    val thumbH = (viewportH * viewportH / contentH).coerceAtLeast(22f)
    val thumbY = y + (scroll / (contentH - viewportH)) * (viewportH - thumbH)
    NanoVGRenderer.drawRoundedRect(x, y, 2f, viewportH, 1f, NanoVGColor(1f, 1f, 1f, 0.07f * alpha))
    NanoVGRenderer.drawRoundedRect(x, thumbY, 2f, thumbH, 1f, NanoVGColor(0.78f, 0.79f, 0.82f, 0.52f * alpha))
}
