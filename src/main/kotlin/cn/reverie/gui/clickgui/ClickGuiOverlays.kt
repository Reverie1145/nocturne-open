package cn.reverie.gui.clickgui

import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.art.AnimeArt
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextLayout
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer

internal fun ClickGuiScreen.renderMinimalCommandBar(
    mouseX: Float, mouseY: Float, layout: ClickGuiCommandBarLayout, theme: NanoVGColor
) {
    val barX = layout.bounds.x
    val barY = layout.bounds.y
    val barW = layout.bounds.width
    val barH = layout.bounds.height
    val centerY = layout.bounds.centerY
    val p = layout.progress
    val focus = layout.focus
    val isHoverBar = p >= 0.95f && layout.bounds.contains(mouseX, mouseY)

    val barVy = if (prevBarY.isNaN()) 0f else (barY - prevBarY)
    prevBarY = barY

    if (kotlin.math.abs(barVy) > 0.15f && p < 0.99f) {
        cn.reverie.render.animation.MotionBlurHelper.renderVerticalMotionBlur(
            barX, barY, barW, barH,
            barH * 0.5f,
            barVy,
            NanoVGColor(1f, 1f, 1f, 0.38f),
            maxBlur = 16f
        )
    }

    val pAlpha = HUDModule.bgOpacity.getFloat()
    val baseBarAlpha = (pAlpha * 0.72f).coerceIn(0.12f, 0.95f)
    val focusBarAlpha = (pAlpha * 0.92f).coerceIn(0.20f, 1.0f)
    val barBg = NanoVGColor(0.04f, 0.04f, 0.06f, baseBarAlpha).lerp(NanoVGColor(0.07f, 0.07f, 0.09f, focusBarAlpha), focus)
    if (HUDModule.widgetShadow.value) Draw2D.shadow(barX, barY, barW, barH, barH * 0.5f, 10f, NanoVGColor(0f, 0f, 0f, 0.38f * HUDModule.shadowStrength.getFloat()))

    try {
        RenderEngine.backdropBlurRenderer.renderBackdrop(
            barX, barY, barW, barH,
            radius = barH * 0.5f,
            tintColor = barBg,
            blurRadius = HUDModule.blurRadius.getFloat(),
            enableBlur = HUDModule.blur.value,
            backdropOpacity = pAlpha
        )
    } catch (ignored: Throwable) {
        NanoVGRenderer.drawRoundedRect(barX, barY, barW, barH, barH * 0.5f, barBg)
    }

    val outlineAlpha = (0.05f + 0.16f * focus + if (isHoverBar) 0.06f else 0f).coerceIn(0f, 0.25f)
    NanoVGRenderer.drawRoundedOutline(
        barX, barY, barW, barH, barH * 0.5f,
        NanoVGColor(1f, 1f, 1f, outlineAlpha),
        1.0f
    )

    val brandX = barX + 11f
    val brandBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(p, 6.0f)
    val comboActive = logoClickCount > 0 && System.currentTimeMillis() - lastLogoClickTime < 1600L
    val moonColor = if (comboActive) {
        val clickRatio = (logoClickCount / 5f).coerceIn(0f, 1f)
        NanoVGColor.WHITE.lerp(theme, clickRatio)
    } else {
        NanoVGColor(0.85f, 0.88f, 0.95f, 0.90f)
    }
    HudVectorIcons.drawMoon(brandX, centerY - 5.5f, 11f, moonColor)

    val brandTextX = brandX + 14f
    NanoVGRenderer.drawVerticallyCenteredString(
        "NOCTURNE",
        brandTextX, centerY,
        if (comboActive) theme.withAlpha(0.95f) else NanoVGColor(0.85f, 0.88f, 0.92f, 0.90f),
        10.5f,
        shadow = HUDModule.textShadow.value,
        blur = brandBlur
    )

    val divX = layout.reset.x2
    Draw2D.line(divX, barY + 6f, divX, barY + barH - 6f, NanoVGColor(1f, 1f, 1f, 0.08f), 1f)

    val searchIconX = divX + 10f
    val searchIconColor = NanoVGColor(0.50f, 0.52f, 0.60f, 0.70f).lerp(NanoVGColor.WHITE, focus)
    CategoryVectorIcons.drawSearchIcon(searchIconX, barY + (barH - 10f) * 0.5f, 10f, searchIconColor)

    val text = layout.search.text
    val textW = NanoVGRenderer.getTextWidth(searchText, 10.5f)
    val textOffset = if (isSearchFocused) (textW - text.width + 2f).coerceAtLeast(0f) else 0f
    NanoVGRenderer.withScissor(text.x, text.y, text.width, text.height) {
        if (searchText.isEmpty() && !isSearchFocused) {
            NanoVGRenderer.drawVerticallyCenteredString("Search...", text.x, centerY, NanoVGColor(0.40f, 0.42f, 0.48f, 0.60f), 10.5f)
        } else {
            NanoVGRenderer.drawVerticallyCenteredString(searchText, text.x - textOffset, centerY, NanoVGColor.WHITE, 10.5f)
            if (isSearchFocused && ((System.currentTimeMillis() - cursorTimer) / 500) % 2 == 0L) {
                val cursorX = (text.x + textW - textOffset + 1f).coerceAtMost(text.x2 - 1.5f)
                Draw2D.line(cursorX, centerY - 5f, cursorX, centerY + 5f, NanoVGColor(0.95f, 0.95f, 0.98f, 0.9f), 1.2f)
            }
        }
    }
    if (searchText.isNotEmpty()) {
        val clear = layout.search.clearButton
        val clearHovered = clear.contains(mouseX, mouseY)
        CategoryVectorIcons.drawClearIcon(clear.centerX - 4f, clear.centerY - 4f, 8f,
            if (clearHovered) NanoVGColor.WHITE else NanoVGColor(0.60f, 0.62f, 0.70f, 0.70f))
    }

    fun drawButton(label: String, bounds: Rect2f, active: Boolean = false) {
        val hovered = bounds.contains(mouseX, mouseY)
        val emphasis = hovered || active
        val bg = if (hovered) theme.withAlpha(0.35f) else if (active) theme.withAlpha(0.22f) else NanoVGColor(1f, 1f, 1f, 0.08f)
        Draw2D.roundedRect(bounds.x, bounds.y, bounds.width, bounds.height, 3.5f, bg)
        Draw2D.roundedOutline(bounds.x, bounds.y, bounds.width, bounds.height, 3.5f,
            if (emphasis) theme else NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)
        NanoVGRenderer.drawVerticallyCenteredString(label,
            bounds.centerX - NanoVGRenderer.getTextWidth(label, 9f) * 0.5f, bounds.centerY,
            if (emphasis) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.95f, 0.9f), 9f)
    }
    drawButton("IRC", layout.irc, ircOpen)
    drawButton("CFG", layout.config, configPanel.isOpen)
    drawButton("HUD", layout.hud)

    NanoVGRenderer.drawVerticallyCenteredString(
        layout.statusText,
        layout.status.x,
        centerY,
        NanoVGColor(0.45f, 0.48f, 0.55f, 0.75f),
        9.5f
    )
}

internal fun ClickGuiScreen.renderEmptySearchCard(screenW: Float, screenH: Float, alpha: Float) {
    val cardW = 240f
    val cardH = 132f
    val cardX = (screenW - cardW) * 0.5f
    val cardY = (screenH - cardH) * 0.5f
    val radius = 7f

        if (HUDModule.widgetShadow.value) Draw2D.shadow(cardX, cardY, cardW, cardH, radius, 10f, NanoVGColor(0f, 0f, 0f, 0.45f * alpha * HUDModule.shadowStrength.getFloat()))
    try {
        RenderEngine.backdropBlurRenderer.renderBackdrop(
            cardX, cardY, cardW, cardH,
            radius = radius,
            tintColor = NanoVGColor(0.05f, 0.055f, 0.075f, 0.88f * alpha * HUDModule.bgOpacity.getFloat()),
            blurRadius = HUDModule.blurRadius.getFloat(),
            enableBlur = HUDModule.blur.value,
            backdropOpacity = alpha * HUDModule.bgOpacity.getFloat()
        )
    } catch (ignored: Throwable) {
        NanoVGRenderer.drawRoundedRect(cardX, cardY, cardW, cardH, radius, NanoVGColor(0.05f, 0.055f, 0.075f, 0.95f * alpha * HUDModule.bgOpacity.getFloat()))
    }
    NanoVGRenderer.drawRoundedOutline(cardX, cardY, cardW, cardH, radius, NanoVGColor(1f, 1f, 1f, 0.10f * alpha), 1f)

    AnimeArt.drawFitted(
        AnimeArt.EMPTY_SEARCH_ART,
        cardX + 10f, cardY + 12f, 64f, cardH - 24f,
        alpha = alpha
    )

    val textX = cardX + 86f
    NanoVGRenderer.drawString("No modules found", textX, cardY + 46f, NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f * alpha), 11f)
    NanoVGRenderer.drawString("\"$searchText\"", textX, cardY + 64f, NanoVGColor(0.55f, 0.58f, 0.65f, 0.85f * alpha), 9f)
}

internal fun ClickGuiScreen.renderTooltipOverlay(tooltip: ClickGuiTooltipInfo, screenW: Float, screenH: Float, alpha: Float) {
    val title = tooltip.title
    val desc = tooltip.description

    val font = RenderEngine.fontRenderer.activeFont
    val maxDescW = 190f
    val layout = TextLayout(font, desc, fontSize = RenderEngine.fontRenderer.resolveSize(9.5f), maxWidth = maxDescW, lineSpacing = 3.0f)

    val titleW = RenderEngine.fontRenderer.getStringWidth(title, 10.5f)
    val contentW = maxOf(titleW, layout.totalWidth)
    val cardW = contentW + 18f
    val titleH = 11f
    val cardH = 8f + titleH + 4f + layout.totalHeight + 7f

    var tipX = tooltip.mouseX + 12f
    if (tipX + cardW > screenW - 8f) {
        tipX = tooltip.mouseX - cardW - 8f
    }
    tipX = tipX.coerceIn(8f, screenW - cardW - 8f)

    var tipY = tooltip.mouseY + 14f
    if (tipY + cardH > screenH - 8f) {
        tipY = tooltip.mouseY - cardH - 8f
    }
    tipY = tipY.coerceIn(8f, screenH - cardH - 8f)

    val floatOffset = (1f - alpha) * 4f
    val renderTipY = tipY - floatOffset

    if (HUDModule.widgetShadow.value) Draw2D.shadow(tipX, renderTipY, cardW, cardH, 4f, 8f, NanoVGColor(0f, 0f, 0f, 0.45f * alpha * HUDModule.shadowStrength.getFloat()))
    try {
        RenderEngine.backdropBlurRenderer.renderBackdrop(
            tipX, renderTipY, cardW, cardH,
            radius = 4f,
            tintColor = NanoVGColor(0.07f, 0.07f, 0.09f, 0.75f * alpha * HUDModule.bgOpacity.getFloat()),
            blurRadius = HUDModule.blurRadius.getFloat(),
            enableBlur = HUDModule.blur.value,
            backdropOpacity = alpha * HUDModule.bgOpacity.getFloat()
        )
    } catch (ignored: Throwable) {
        NanoVGRenderer.drawRoundedRect(tipX, renderTipY, cardW, cardH, 4f, NanoVGColor(0.07f, 0.07f, 0.09f, 0.95f * alpha * HUDModule.bgOpacity.getFloat()))
    }

    NanoVGRenderer.drawRoundedOutline(
        tipX, renderTipY, cardW, cardH,
        4f,
        NanoVGColor(1f, 1f, 1f, 0.14f * alpha),
        1f
    )

    val tipBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(alpha, 5.5f)

    NanoVGRenderer.drawString(title, tipX + 8f, renderTipY + 6f, NanoVGColor(1f, 1f, 1f, alpha), 10.5f, shadow = HUDModule.textShadow.value, blur = tipBlur)

    val descStartY = renderTipY + 6f + titleH + 4f
    for (line in layout.lines) {
        NanoVGRenderer.drawString(
            line.text,
            tipX + 8f,
            descStartY + line.yOffset,
            NanoVGColor(0.68f, 0.70f, 0.76f, 0.90f * alpha),
            9.5f,
            shadow = HUDModule.textShadow.value,
            blur = tipBlur
        )
    }
}
