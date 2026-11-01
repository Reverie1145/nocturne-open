package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MenuFrame
import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.mainmenu.head
import cn.reverie.gui.mainmenu.mulA
import cn.reverie.gui.mainmenu.pageHeader
import cn.reverie.gui.mainmenu.stage
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox

internal fun VanillaListScreenRenderer.suppressVanillaWidgets() {
    for (child in screen.children()) {
        if (child is Button) {
            child.x = -9999
        } else if (child is EditBox) {
            child.x = -9999
        }
    }
}

internal fun VanillaListScreenRenderer.getCountText(): String {
    val list = findList() ?: return ""
    val count = list.children().size
    return if (isSingleplayer) "$count worlds" else "$count servers"
}

/** Everything between the background and the persistent chrome: header, toolbar, list and detail panels. */
internal fun VanillaListScreenRenderer.renderSubScreen(f: MenuFrame, rel: Float, mouseX: Float, mouseY: Float, dt: Float) {
    val k = f.k
    val tf = RenderEngine.nanoVG.transform

    val titleCy = f.titleCy
    val subCy = f.subCy

    val count = getCountText()
    val tagline = if (isSingleplayer) "Pick up where you left off, or start something new." else "Join a server and play together with friends."
    pageHeader(
        f, rel, 1f, "", "PLAY",
        if (isSingleplayer) "Singleplayer" else "Multiplayer",
        if (count.isEmpty()) tagline else "$count  \u00b7  $tagline"
    )

    // Back (same slot as the page title, right aligned).
    val stBack = stage(rel, 1, f.h)
    if (stBack.vis > 0.004f) {
        tf.save()
        tf.translate(0f, stBack.dy * 0.5f)
        val bw = 108f
        val bh = 38f
        renderPillButton(backButton, f.w - f.margin - bw, titleCy - bh * 0.5f, bw, bh, stBack.vis, mouseX, mouseY)
        tf.restore()
    }

    // Toolbar: search and page actions.
    val toolH = 38f
    val toolY = subCy + 26f
    val stTool = stage(rel, 3, f.h)
    if (stTool.vis > 0.004f) {
        tf.save()
        tf.translate(0f, stTool.dy * 0.5f)
        val searchW = 290f
        drawSearchField(f.margin, toolY, searchW, toolH, stTool.vis, mouseX, mouseY)
        var bx = f.margin + searchW + 12f
        for (btn in toolbarButtons) {
            val labelW = head { NanoVGRenderer.getTextWidth(btn.label, 12.5f) }
            val bw = labelW + 40f
            renderPillButton(btn, bx, toolY, bw, toolH, stTool.vis, mouseX, mouseY)
            bx += bw + 10f
        }
        tf.restore()
    }

    // Panels.
    val gap = 24f
    val panelY = toolY + toolH + 20f
    val panelH = (f.h - 78f * k.coerceAtMost(1.2f) - panelY).coerceAtLeast(220f)
    val listW = (f.contentW - gap) * 0.585f
    val detailX = f.margin + listW + gap
    val detailW = f.contentW - listW - gap

    val list = findList()
    if (list != null) {
        renderContentList(list, f, f.margin, panelY, listW, panelH, rel, mouseX, mouseY, dt)
    }
    renderDetailPanel(f, detailX, panelY, detailW, panelH, rel, mouseX, mouseY)
}

/** Dark, inset search field: the same construction as the ClickGUI search and the top navigation container. */
internal fun VanillaListScreenRenderer.drawSearchField(
    x: Float, y: Float, w: Float, h: Float, a: Float, mouseX: Float, mouseY: Float
) {
    searchBoxRect = VanillaListScreenRenderer.RectF(x, y, w, h)
    searchFocusAnim.animateTo(if (searchFocused) 1f else 0f)
    val fa = searchFocusAnim.value
    val hovered = searchBoxRect.contains(mouseX, mouseY)
    val r = 11f
    val cy = y + h * 0.5f

    Draw2D.roundedRect(x, y, w, h, r, NanoVGColor(0f, 0f, 0f, (0.24f + 0.10f * fa) * a))
    val outline = white(if (hovered) 0.16f else 0.10f).lerp(MenuTheme.ACCENT, 0.75f * fa)
    Draw2D.roundedOutline(x, y, w, h, r, outline.mulA((1f + 0.4f * fa) * a), 1f)
    if (fa > 0.01f) Draw2D.glow(x, y, w, h, r, 14f, MenuTheme.ACCENT.mulA(0.14f * fa * a))

    MenuTheme.drawSearchIcon(x + 20f, cy, 13f, white(0.50f + 0.45f * fa).mulA(a))

    val hint = searchHint?.string ?: (if (isSingleplayer) "Search worlds..." else "Search servers...")
    val textX = x + 38f
    val textMaxW = w - 38f - 34f
    if (searchQuery.isEmpty()) {
        NanoVGRenderer.drawVerticallyCenteredString(hint, textX, cy, MenuTheme.TEXT_MUTED.mulA(0.70f * a), 12.5f)
        if (searchFocused && ((System.currentTimeMillis() - cursorTimer) / 500) % 2L == 0L) {
            Draw2D.line(textX, cy - 7f, textX, cy + 7f, MenuTheme.ACCENT.mulA(a), 1.3f)
        }
    } else {
        val fitted = NanoVGRenderer.trimWithEllipsis(searchQuery, textMaxW, 12.5f)
        val drawn = NanoVGRenderer.drawVerticallyCenteredString(fitted, textX, cy, MenuTheme.TEXT.mulA(a), 12.5f)
        if (searchFocused && ((System.currentTimeMillis() - cursorTimer) / 500) % 2L == 0L) {
            val cx = (textX + drawn + 1.5f).coerceAtMost(x + w - 30f)
            Draw2D.line(cx, cy - 7f, cx, cy + 7f, MenuTheme.ACCENT.mulA(a), 1.3f)
        }
        val clearSize = 18f
        searchClearRect = VanillaListScreenRenderer.RectF(x + w - 14f - clearSize * 0.5f - 6f, cy - clearSize * 0.5f, clearSize + 6f, clearSize)
        MenuTheme.drawClearIcon(x + w - 22f, cy, 11f, white(if (searchClearRect.contains(mouseX, mouseY)) 0.95f else 0.60f).mulA(a))
        return
    }
    searchClearRect = VanillaListScreenRenderer.RectF(0f, 0f, 0f, 0f)
}

/** Pill button drawn with the same [MenuTheme.drawButton] styles as the main menu hero buttons. */
internal fun VanillaListScreenRenderer.renderPillButton(
    btn: VanillaListScreenRenderer.InteractivePillButton,
    x: Float, y: Float, w: Float, h: Float,
    alpha: Float, mouseX: Float, mouseY: Float,
    withArrow: Boolean = false
) {
    btn.x = x
    btn.y = y
    btn.w = w
    btn.h = h

    val isHover = btn.contains(mouseX, mouseY) && btn.isEnabled && closeStartMs == 0L
    btn.isHovered = isHover
    btn.hoverAnim.animateTo(if (isHover) 1f else 0f)
    btn.pressAnim.animateTo(if (btn.isPressed) 1f else 0f)

    val style = when {
        btn.isDanger -> MenuTheme.ButtonStyle.DANGER_GHOST
        btn.isPrimary -> MenuTheme.ButtonStyle.PRIMARY
        else -> MenuTheme.ButtonStyle.GHOST
    }

    MenuTheme.drawButton(
        x, y, w, h,
        label = btn.label,
        style = style,
        hover = btn.hoverAnim.value,
        press = btn.pressAnim.value,
        alpha = alpha,
        enabled = btn.isEnabled,
        withArrow = withArrow,
        leadingBackArrow = btn === backButton
    )
}
