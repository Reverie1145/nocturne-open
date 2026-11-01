package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MenuFrame
import cn.reverie.gui.mainmenu.MenuFx.drawTracked
import cn.reverie.gui.mainmenu.MenuFx.glowFill
import cn.reverie.gui.mainmenu.MenuFx.trackedWidth
import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.mainmenu.MenuFx.withTilt
import cn.reverie.gui.mainmenu.MenuScene
import cn.reverie.gui.mainmenu.drawSurface
import cn.reverie.gui.mainmenu.head
import cn.reverie.gui.mainmenu.mulA
import cn.reverie.gui.mainmenu.stage
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.mixin.gui.NetworkServerEntryAccessor
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import org.lwjgl.nanovg.NanoVG.nvgRoundedRect
import kotlin.math.max

private val TEXT = MenuTheme.TEXT
private val MUTED = MenuTheme.TEXT_MUTED
private val ACCENT = MenuTheme.ACCENT

/**
 * The right-hand panel: a large preview of the selected world or server and its actions. It plays the role the big
 * cards play on the main menu's Play page (tinted orb, drifting layers, primary pill button).
 */
internal fun VanillaListScreenRenderer.renderDetailPanel(
    f: MenuFrame, x: Float, y: Float, w: Float, h: Float,
    rel: Float, mouseX: Float, mouseY: Float
) {
    val st = stage(rel, 6, f.h)
    val a = st.vis
    if (a <= 0.004f) return

    val selected = findList()?.selected
    for (btn in dockButtons) btn.isEnabled = !btn.needsSelection || selected != null

    val tf = RenderEngine.nanoVG.transform
    tf.save()
    tf.translate(0f, st.dy)
    withTilt(x + w * 0.5f, y + h * 0.5f, st.rot, -st.rot * 0.25f, 0.94f + 0.06f * a) {
        val r = 20f
        val pad = 28f
        drawSurface(x, y, w, h, r, a, 0f, 0.50f)

        // Depth layers drift against the pointer, like the orb on the main menu cards.
        val px = MenuScene.parX
        val py = MenuScene.parY
        glowFill(x + w * 0.84f - px * 18f, y + h * 0.12f - py * 12f, w * 0.72f, ACCENT.mulA(0.20f * a)) { c ->
            nvgRoundedRect(c, x, y, w, h, r)
        }
        glowFill(x, y + h, w * 0.6f, MenuTheme.ACCENT_WARM.mulA(0.08f * a)) { c -> nvgRoundedRect(c, x, y, w, h, r) }

        val iconSize = 76f
        val ix = x + pad - px * 3f
        val iy = y + pad - py * 2f
        val tx = ix + iconSize + 18f
        val textW = x + w - pad - tx

        when (selected) {
            is WorldSelectionList.WorldListEntry -> {
                val summary = selected.levelSummary
                val dim = !summary.isCompatible || summary.isLocked || summary.isDisabled
                drawDetailIcon(loadIconFromFile(summary.icon, summary.levelId), ix, iy, iconSize, a)
                drawDetailTitle(tx, iy, textW, "WORLD", summary.levelName, "Last played  " + VanillaListScreenRenderer.formatDate(summary.lastPlayed), a)

                var ry = iy + iconSize + 30f
                ry = drawSeparator(x + pad, x + w - pad, ry - 14f, a) + 14f
                ry = drawInfoRow(x + pad, x + w - pad, ry, "Game mode", summary.info.string, null, a)
                val ver = summary.worldVersionName.string
                ry = drawInfoRow(
                    x + pad, x + w - pad, ry, "Version", if (ver.isEmpty()) "Unknown" else ver,
                    if (!summary.isCompatible) MenuTheme.DANGER else null, a
                )
                val status = when {
                    summary.isLocked -> "Locked"
                    summary.isDisabled || !summary.isCompatible -> "Unavailable"
                    else -> "Ready"
                }
                drawInfoRow(x + pad, x + w - pad, ry, "Status", status, if (dim) MenuTheme.DANGER else null, a)
            }
            is ServerSelectionList.OnlineServerEntry -> {
                val data = selected.serverData
                val cacheId = System.identityHashCode(selected) * 31L + (data.iconBytes?.contentHashCode()?.toLong() ?: 0L)
                drawDetailIcon(loadIconFromBytes(data.iconBytes, cacheId), ix, iy, iconSize, a)
                drawDetailTitle(tx, iy, textW, "SERVER", data.name, data.ip, a)

                var ry = iy + iconSize + 30f
                ry = drawSeparator(x + pad, x + w - pad, ry - 14f, a) + 14f
                val players = data.players?.let { "${it.online()} / ${it.max()}" } ?: "--"
                ry = drawInfoRow(x + pad, x + w - pad, ry, "Players", players, null, a)
                val ping = data.ping
                ry = drawInfoRow(
                    x + pad, x + w - pad, ry, "Latency", if (ping < 0) "Pinging..." else "$ping ms", pingColor(ping).copy(a = 1f), a
                )
                val motd = data.motd.string.replace('\n', ' ').trim()
                drawInfoRow(x + pad, x + w - pad, ry, "Message", motd.ifEmpty { "No message" }, null, a, cjk = true)
            }
            is ServerSelectionList.NetworkServerEntry -> {
                val lan = (selected as NetworkServerEntryAccessor).`nocturne$serverData`()
                drawDetailIcon(null, ix, iy, iconSize, a)
                drawDetailTitle(tx, iy, textW, "LAN SERVER", lan.motd, lan.address, a)
                var ry = iy + iconSize + 30f
                ry = drawSeparator(x + pad, x + w - pad, ry - 14f, a) + 14f
                ry = drawInfoRow(x + pad, x + w - pad, ry, "Network", "Local area", null, a)
                drawInfoRow(x + pad, x + w - pad, ry, "Address", lan.address, null, a)
            }
            else -> {
                val cx = x + w * 0.5f
                val cy = y + h * 0.5f - 62f
                Draw2D.circleOutline(cx, cy, 42f, white(0.07f).mulA(a), 1f)
                Draw2D.circleOutline(cx, cy, 30f, white(0.12f).mulA(a), 1f)
                MenuTheme.drawCrescent(cx, cy, 11f, a)
                head {
                    NanoVGRenderer.drawCenteredString(
                        if (isSingleplayer) "Select a world" else "Select a server", cx, cy + 66f, TEXT.mulA(0.92f * a), 17f
                    )
                }
                NanoVGRenderer.drawCenteredString(
                    "Details and actions appear here.", cx, cy + 90f, MUTED.mulA(0.70f * a), 12f
                )
            }
        }

        // Actions: one primary pill, then the secondary ones side by side.
        val primary = dockButtons.firstOrNull { it.isPrimary }
        val secondary = dockButtons.filter { it !== primary }
        val secH = 40f
        val priH = 52f
        val secY = y + h - pad - secH
        val priY = secY - 12f - priH
        if (primary != null) {
            renderPillButton(primary, x + pad, priY, w - pad * 2f, priH, a, mouseX, mouseY, withArrow = true)
        }
        if (secondary.isNotEmpty()) {
            val g = 10f
            val bw = (w - pad * 2f - g * (secondary.size - 1)) / secondary.size
            for ((i, btn) in secondary.withIndex()) {
                renderPillButton(btn, x + pad + i * (bw + g), secY, bw, secH, a, mouseX, mouseY)
            }
        }
    }
    tf.restore()
}

private fun VanillaListScreenRenderer.drawDetailIcon(
    image: cn.reverie.render.nanovg.NanoVGImage?, x: Float, y: Float, size: Float, a: Float
) {
    val r = size * 0.2f
    Draw2D.glow(x, y, size, size, r, 22f, ACCENT.mulA(0.26f * a))
    drawIcon(image, x, y, size, a)
    Draw2D.roundedOutline(x, y, size, size, r, white(0.16f).mulA(a), 1f)
}

private fun drawDetailTitle(tx: Float, iy: Float, maxW: Float, tag: String, name: String, subline: String, a: Float) {
    head { drawTracked(tag, tx, iy + 10f, ACCENT.mulA(0.95f * a), 9f, 2.0f) }
    var shown = name
    cn.reverie.gui.mainmenu.cjk {
        shown = NanoVGRenderer.trimWithEllipsis(name, maxW, 24f)
        NanoVGRenderer.drawVerticallyCenteredString(shown, tx, iy + 38f, TEXT.mulA(a), 24f)
    }
    cn.reverie.gui.mainmenu.cjk {
        NanoVGRenderer.drawVerticallyCenteredString(
            NanoVGRenderer.trimWithEllipsis(subline, maxW, 12.5f), tx, iy + 66f, MUTED.mulA(0.78f * a), 12.5f
        )
    }
}

internal fun drawSeparator(x0: Float, x1: Float, y: Float, a: Float): Float {
    Draw2D.rect(x0, y, x1 - x0, 1f, white(0.09f).mulA(a))
    return y
}

/** Label on the left, value on the right; returns the y of the next row. */
internal fun drawInfoRow(
    x0: Float, x1: Float, y: Float, label: String, value: String, valueColor: NanoVGColor?, a: Float, cjk: Boolean = false
): Float {
    val rowH = 30f
    val cy = y + rowH * 0.5f
    head { drawTracked(label.uppercase(), x0, cy, MUTED.mulA(0.72f * a), 8.5f, 1.6f) }
    val labelW = head { trackedWidth(label.uppercase(), 8.5f, 1.6f) }
    val maxW = max(40f, x1 - x0 - labelW - 24f)
    val color = (valueColor ?: TEXT).mulA((if (valueColor != null) 1f else 0.92f) * a)
    if (cjk) {
        cn.reverie.gui.mainmenu.cjk {
            NanoVGRenderer.drawRightVerticallyCenteredString(NanoVGRenderer.trimWithEllipsis(value, maxW, 13f), x1, cy, color, 13f)
        }
    } else {
        NanoVGRenderer.drawRightVerticallyCenteredString(NanoVGRenderer.trimWithEllipsis(value, maxW, 13f), x1, cy, color, 13f)
    }
    return y + rowH
}
