package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MenuFrame
import cn.reverie.gui.mainmenu.MenuFx
import cn.reverie.gui.mainmenu.MenuFx.glowFill
import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.mainmenu.MenuFx.withTilt
import cn.reverie.gui.mainmenu.drawSurface
import cn.reverie.gui.mainmenu.head
import cn.reverie.gui.mainmenu.mulA
import cn.reverie.gui.mainmenu.stage
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.mixin.gui.WorldSelectionListAccessor
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/** Row metrics shared by the renderer, the painters and the input handling. */
internal const val ROW_H = 70f
internal const val ROW_GAP = 8f
internal const val ROW_PAD = 10f
internal const val QUICK_SIZE = 34f
internal const val QUICK_MARGIN = 14f

internal fun VanillaListScreenRenderer.renderContentList(
    list: ObjectSelectionList<*>,
    f: MenuFrame,
    x: Float, y: Float, w: Float, h: Float,
    rel: Float, mouseX: Float, mouseY: Float, dt: Float
) {
    if (list is WorldSelectionList) {
        val accessor = list as WorldSelectionListAccessor
        val levels = accessor.`nocturne$pollLevelsIgnoreErrors`()
        if (levels != null && levels !== lastLevels) {
            lastLevels = levels
            accessor.`nocturne$handleNewLevels`(levels)
        }
    }

    val allChildren = list.children()
    val entries: List<Any> = if (searchQuery.isBlank() || isSingleplayer) {
        allChildren
    } else {
        allChildren.filter { entry -> matchesSearch(entry, searchQuery) }
    }
    filtered = entries
    listRect = VanillaListScreenRenderer.RectF(x, y, w, h)

    val totalH = entries.size * ROW_H + (entries.size - 1).coerceAtLeast(0) * ROW_GAP
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
    val selectedEntry = list.selected
    val overList = listRect.contains(mouseX, mouseY) && closeStartMs == 0L

    tf.save()
    tf.translate(0f, st.dy)
    withTilt(x + w * 0.5f, y + h * 0.5f, st.rot, -st.rot * 0.25f, 0.94f + 0.06f * a) {
        drawSurface(x, y, w, h, 20f, a, 0f, 0.46f)

        nvgSave(ctx)
        nvgScissor(ctx, x + 1f, y + 1f, w - 2f, h - 2f)

        if (entries.isEmpty()) {
            val msg = if (searchQuery.isNotEmpty()) "No matches for \"$searchQuery\"" else if (isSingleplayer) "No worlds yet" else "No servers yet"
            val hint = if (searchQuery.isNotEmpty()) "Try a different search." else if (isSingleplayer) "Create one with New World." else "Add one with Add Server."
            Draw2D.circleOutline(x + w * 0.5f, y + h * 0.5f - 30f, 26f, white(0.14f).mulA(a), 1f)
            MenuTheme.drawCrescent(x + w * 0.5f, y + h * 0.5f - 30f, 9f, a)
            head { NanoVGRenderer.drawCenteredString(msg, x + w * 0.5f, y + h * 0.5f + 20f, MenuTheme.TEXT.mulA(0.92f * a), 16f) }
            NanoVGRenderer.drawCenteredString(hint, x + w * 0.5f, y + h * 0.5f + 44f, MenuTheme.TEXT_MUTED.mulA(0.70f * a), 12f)
        } else {
            val cardW = w - ROW_PAD * 2f - (if (scrollMax > 0f) 6f else 0f)
            val cardX = x + ROW_PAD

            var cy = y + ROW_PAD - scrollPos
            for ((index, entry) in entries.withIndex()) {
                if (cy + ROW_H >= y && cy <= y + h) {
                    val rect = VanillaListScreenRenderer.RectF(cardX, cy, cardW, ROW_H)
                    val quick = VanillaListScreenRenderer.RectF(
                        rect.x + rect.width - QUICK_SIZE - QUICK_MARGIN, rect.y + (ROW_H - QUICK_SIZE) * 0.5f, QUICK_SIZE, QUICK_SIZE
                    )
                    visibleRows.add(VanillaListScreenRenderer.RowHit(entry, rect, quick))

                    // Rows arrive one after another, the same stagger the main menu pages use.
                    val rs = stage(rel, 5 + min(index, 6), f.h)
                    val isHover = overList && rect.contains(mouseX, mouseY)
                    renderRow(entry, rect, quick, isHover, entry === selectedEntry, rs.vis * a, rs.dy * 0.3f, mouseX, mouseY, dt)
                }
                cy += ROW_H + ROW_GAP
            }
        }

        nvgResetScissor(ctx)
        nvgRestore(ctx)

        renderScrollbar(x, y, w, h, scrollPos, scrollMax, a, mouseX, mouseY)
    }
    tf.restore()
}

internal fun VanillaListScreenRenderer.matchesSearch(entry: Any, query: String): Boolean {
    val q = query.lowercase()
    return when (entry) {
        is ServerSelectionList.OnlineServerEntry -> {
            val data = entry.serverData
            data.name.lowercase().contains(q) || data.ip.lowercase().contains(q) || (data.motd.string.lowercase().contains(q))
        }
        is ServerSelectionList.NetworkServerEntry -> {
            val lan = (entry as cn.reverie.mixin.gui.NetworkServerEntryAccessor).`nocturne$serverData`()
            lan.motd.lowercase().contains(q) || lan.address.lowercase().contains(q)
        }
        else -> true
    }
}

/** One list row: glass chip with a pointer-following sheen, an accent bar when selected and a hover arrow. */
internal fun VanillaListScreenRenderer.renderRow(
    entry: Any,
    rect: VanillaListScreenRenderer.RectF,
    quick: VanillaListScreenRenderer.RectF,
    hover: Boolean, selected: Boolean,
    alpha: Float, dy: Float,
    mouseX: Float, mouseY: Float, dt: Float
) {
    if (alpha <= 0.004f) return
    val key = System.identityHashCode(entry)
    val target = if (hover) 1f else 0f
    val cur = cardHover.getOrDefault(key, target)
    val hv = cur + (target - cur) * (1f - exp(-dt * 14f))
    cardHover[key] = hv

    val ctx = RenderEngine.nanoVG.handle
    val tf = RenderEngine.nanoVG.transform
    val accent = MenuTheme.ACCENT

    nvgSave(ctx)
    nvgGlobalAlpha(ctx, alpha.coerceIn(0f, 1f))
    tf.translate(0f, dy)

    val vx = rect.x + 4f * hv
    val r = 15f
    val cy = rect.y + rect.height * 0.5f

    Draw2D.roundedRect(vx, rect.y, rect.width, rect.height, r, white(0.035f + 0.040f * hv + (if (selected) 0.030f else 0f)))
    if (selected) {
        Draw2D.gradientLinear(vx, rect.y, rect.width, rect.height, r, accent.mulA(0.17f), accent.mulA(0.02f), horizontal = true)
    }
    if (hv > 0.01f) {
        val gx = mouseX.coerceIn(vx, vx + rect.width)
        glowFill(gx, cy, rect.width * 0.42f, white(0.075f * hv)) { c -> nvgRoundedRect(c, vx, rect.y, rect.width, rect.height, r) }
    }
    val outline = if (selected) accent.mulA(0.60f) else white(0.07f + 0.12f * hv)
    Draw2D.roundedOutline(vx, rect.y, rect.width, rect.height, r, outline, 1f)
    if (selected) {
        Draw2D.roundedRect(vx + 1f, cy - 15f, 3f, 30f, 1.5f, accent.mulA(0.95f))
    }

    painter.paintEntry(entry, this, vx, rect.y, rect.width, rect.height, hover, selected, dt)

    val showQuick = hv > 0.02f || selected
    if (showQuick) {
        val qa = if (selected) maxOf(hv, 0.55f) else hv
        val qHover = quick.contains(mouseX, mouseY) && hover
        val qx = quick.x + 4f * hv
        val qcx = qx + quick.width * 0.5f
        Draw2D.circle(qcx, cy, quick.width * 0.5f, white(0.07f * qa))
        if (qHover || selected) {
            Draw2D.circle(qcx, cy, quick.width * 0.5f, accent.lerp(MenuTheme.ACCENT_WARM, 0.35f).mulA((if (qHover) 0.95f else 0.22f) * qa))
        }
        Draw2D.circleOutline(qcx, cy, quick.width * 0.5f, white(0.14f * qa), 1f)
        MenuTheme.drawArrow(qcx - 0.5f + (if (qHover) 1.5f else 0f), cy, (if (qHover) MenuTheme.INK_DEEP else MenuTheme.TEXT).mulA(0.95f * qa), if (qHover) 0.6f else 0f)
    }

    nvgRestore(ctx)
}

internal fun VanillaListScreenRenderer.renderScrollbar(
    listX: Float, listY: Float, listW: Float, listH: Float,
    currentScroll: Float, maxScroll: Float, alpha: Float,
    mouseX: Float, mouseY: Float
) {
    scrollbarThumb = null
    if (maxScroll <= 0f) return

    val trackX = listX + listW - 9f
    val trackW = 3f
    val trackTop = listY + 14f
    val trackH = listH - 28f
    val thumbH = (trackH * trackH / (trackH + maxScroll)).coerceIn(28f, trackH)
    val scrollable = trackH - thumbH
    val frac = (currentScroll / maxScroll).coerceIn(0f, 1f)
    val thumbY = trackTop + scrollable * frac

    val thumb = VanillaListScreenRenderer.RectF(trackX, thumbY, trackW, thumbH)
    scrollbarThumb = thumb
    val isHoverThumb = thumb.copy(x = thumb.x - 5f, width = thumb.width + 10f).contains(mouseX, mouseY)
    val active = draggingScrollbar || isHoverThumb

    Draw2D.roundedRect(trackX, trackTop, trackW, trackH, trackW * 0.5f, white(0.04f).mulA(alpha))
    MenuTheme.drawScrollThumb(trackX, thumbY, trackW, thumbH, active, alpha)
}

internal fun VanillaListScreenRenderer.setListSelected(list: ObjectSelectionList<*>, entry: Any?) {
    if (list is WorldSelectionList) {
        list.selected = entry as? WorldSelectionList.Entry
    } else if (list is ServerSelectionList) {
        list.selected = entry as? ServerSelectionList.Entry
    }
}

/** Scrolls just enough to bring the selected row fully into the viewport. */
internal fun VanillaListScreenRenderer.ensureSelectedVisible() {
    val list = findList() ?: return
    val selectedEntry: Any? = list.selected
    val index = filtered.indexOf(selectedEntry)
    if (index < 0) return
    val rowTop = index * (ROW_H + ROW_GAP)
    val viewport = listRect.height - ROW_PAD * 2f
    if (rowTop < scrollTarget) {
        scrollTarget = rowTop
    } else if (rowTop + ROW_H > scrollTarget + viewport) {
        scrollTarget = rowTop + ROW_H - viewport
    }
    scrollTarget = scrollTarget.coerceIn(0f, scrollMax)
}
