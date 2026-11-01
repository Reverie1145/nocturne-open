package cn.reverie.gui.vanilla

import cn.reverie.gui.mainmenu.MenuFx.white
import cn.reverie.gui.mainmenu.cjk
import cn.reverie.gui.mainmenu.head
import cn.reverie.gui.mainmenu.mulA
import cn.reverie.gui.theme.MenuTheme
import cn.reverie.mixin.gui.NetworkServerEntryAccessor
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.SharedConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.network.EventLoopGroupHolder
import java.net.UnknownHostException
import java.util.concurrent.Executors

interface VanillaEntryPainter {
    fun paintEntry(
        entry: Any,
        r: VanillaListScreenRenderer,
        rowLeft: Float, rowTop: Float, rowW: Float, rowH: Float,
        hovered: Boolean, selected: Boolean, dtMs: Float
    )
}

private val NAME_COLOR = NanoVGColor(0.90f, 0.88f, 0.92f, 1f)

private fun nameColor(selected: Boolean, hovered: Boolean): NanoVGColor = when {
    selected -> NanoVGColor.WHITE
    hovered -> NanoVGColor(0.98f, 0.96f, 0.97f, 1f)
    else -> NAME_COLOR
}

/** Latency colour shared by the row badge and the detail panel. */
internal fun pingColor(ping: Long): NanoVGColor = when {
    ping < 0 -> NanoVGColor(0.62f, 0.60f, 0.66f, 0.70f)
    ping < 80L -> NanoVGColor(0.45f, 0.88f, 0.68f, 0.98f)
    ping < 180L -> NanoVGColor(0.97f, 0.78f, 0.42f, 0.98f)
    else -> NanoVGColor(0.97f, 0.46f, 0.46f, 0.98f)
}

/** Space kept free on the right of a row for the hover arrow button. */
private const val RIGHT_RESERVE = QUICK_SIZE + QUICK_MARGIN * 2f

class WorldListEntryPainter : VanillaEntryPainter {

    override fun paintEntry(
        entry: Any,
        r: VanillaListScreenRenderer,
        rowLeft: Float, rowTop: Float, rowW: Float, rowH: Float,
        hovered: Boolean, selected: Boolean, dtMs: Float
    ) {
        when (entry) {
            is WorldSelectionList.WorldListEntry -> {
                val summary = entry.levelSummary
                val dim = !summary.isCompatible || summary.isLocked || summary.isDisabled
                val alpha = if (dim) 0.55f else 1.0f
                val cy = rowTop + rowH * 0.5f

                val iconSize = rowH - 18f
                val iconX = rowLeft + 14f
                r.drawIcon(r.loadIconFromFile(summary.icon, summary.levelId), iconX, rowTop + 9f, iconSize, alpha)

                val textLeft = iconX + iconSize + 16f
                val dateRight = rowLeft + rowW - RIGHT_RESERVE
                val dateText = VanillaListScreenRenderer.formatDate(summary.lastPlayed)
                val dateW = NanoVGRenderer.getTextWidth(dateText, 11f)
                val nameMaxW = dateRight - dateW - 20f - textLeft

                cjk {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        NanoVGRenderer.trimWithEllipsis(summary.levelName, nameMaxW, 14.5f),
                        textLeft, cy - 11f, nameColor(selected, hovered).mulA(alpha), 14.5f
                    )
                }

                val muted = MenuTheme.TEXT_MUTED
                val mode = summary.info.string
                var sx = textLeft
                sx += NanoVGRenderer.drawVerticallyCenteredString(mode, sx, cy + 12f, muted.mulA(0.78f * alpha), 11f)
                val ver = summary.worldVersionName.string
                if (ver.isNotEmpty()) {
                    sx += 7f
                    Draw2D.circle(sx, cy + 12f, 1.4f, white(0.30f).mulA(alpha))
                    sx += 8f
                    val verColor = if (!summary.isCompatible) MenuTheme.DANGER.mulA(0.95f * alpha) else muted.mulA(0.62f * alpha)
                    NanoVGRenderer.drawVerticallyCenteredString(ver, sx, cy + 12f, verColor, 11f)
                }

                NanoVGRenderer.drawRightVerticallyCenteredString(dateText, dateRight, cy, muted.mulA(0.62f * alpha), 11f)
            }
            else -> {
                NanoVGRenderer.drawCenteredString("...", rowLeft + rowW * 0.5f, rowTop + rowH * 0.5f, white(0.4f), 12f)
            }
        }
    }
}

class ServerListEntryPainter : VanillaEntryPainter {

    override fun paintEntry(
        entry: Any,
        r: VanillaListScreenRenderer,
        rowLeft: Float, rowTop: Float, rowW: Float, rowH: Float,
        hovered: Boolean, selected: Boolean, dtMs: Float
    ) {
        val muted = MenuTheme.TEXT_MUTED
        val cy = rowTop + rowH * 0.5f
        val iconSize = rowH - 18f
        val iconX = rowLeft + 14f
        val textLeft = iconX + iconSize + 16f
        val right = rowLeft + rowW - RIGHT_RESERVE

        when (entry) {
            is ServerSelectionList.OnlineServerEntry -> {
                val data = entry.serverData
                ensurePinged(entry, data, r)

                val bytes = data.iconBytes
                val cacheId = System.identityHashCode(entry) * 31L + (bytes?.contentHashCode()?.toLong() ?: 0L)
                r.drawIcon(r.loadIconFromBytes(bytes, cacheId), iconX, rowTop + 9f, iconSize, 1.0f)

                val playersText = data.players?.let { "${it.online()}/${it.max()}" }
                val playersW = if (playersText != null) NanoVGRenderer.getTextWidth(playersText, 11f) + 22f else 0f
                val pingW = 70f
                val badgesTotalW = pingW + (if (playersText != null) playersW + 8f else 0f)
                val badgesLeft = right - badgesTotalW
                val textMaxW = badgesLeft - 18f - textLeft

                cjk {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        NanoVGRenderer.trimWithEllipsis(data.name, textMaxW, 14.5f),
                        textLeft, cy - 11f, nameColor(selected, hovered), 14.5f
                    )
                    val motd = data.motd.string.replace('\n', ' ').ifEmpty { data.ip }
                    NanoVGRenderer.drawVerticallyCenteredString(
                        NanoVGRenderer.trimWithEllipsis(motd, textMaxW, 11f),
                        textLeft, cy + 12f, muted.mulA(0.72f), 11f
                    )
                }

                drawPingBadge(data.ping, right - pingW, cy - 11f, pingW)
                if (playersText != null) {
                    val px = right - pingW - 8f - playersW
                    Draw2D.roundedRect(px, cy - 11f, playersW, 22f, 11f, white(0.05f))
                    Draw2D.roundedOutline(px, cy - 11f, playersW, 22f, 11f, white(0.11f), 1f)
                    NanoVGRenderer.drawCenteredString(playersText, px + playersW * 0.5f, cy, NanoVGColor(0.86f, 0.86f, 0.94f, 0.92f), 11f)
                }
            }
            is ServerSelectionList.NetworkServerEntry -> {
                val lan = (entry as NetworkServerEntryAccessor).`nocturne$serverData`()
                r.drawIcon(null, iconX, rowTop + 9f, iconSize, 1.0f)

                val tagW = 44f
                val tagX = right - tagW
                val textMaxW = tagX - 18f - textLeft
                cjk {
                    NanoVGRenderer.drawVerticallyCenteredString(
                        NanoVGRenderer.trimWithEllipsis(lan.motd, textMaxW, 14.5f),
                        textLeft, cy - 11f, nameColor(selected, hovered), 14.5f
                    )
                }
                NanoVGRenderer.drawVerticallyCenteredString(lan.address, textLeft, cy + 12f, muted.mulA(0.72f), 11f)

                Draw2D.roundedRect(tagX, cy - 11f, tagW, 22f, 11f, NanoVGColor(0.45f, 0.75f, 1f, 0.13f))
                Draw2D.roundedOutline(tagX, cy - 11f, tagW, 22f, 11f, NanoVGColor(0.45f, 0.75f, 1f, 0.38f), 1f)
                NanoVGRenderer.drawCenteredString("LAN", tagX + tagW * 0.5f, cy, NanoVGColor(0.65f, 0.87f, 1f, 0.97f), 10.5f)
            }
            is ServerSelectionList.LANHeader -> {
                NanoVGRenderer.drawCenteredString(
                    Component.translatable("lanServer.title").string,
                    rowLeft + rowW * 0.5f, cy, muted.mulA(0.80f), 12f
                )
            }
            else -> {
                NanoVGRenderer.drawCenteredString("...", rowLeft + rowW * 0.5f, cy, white(0.4f), 12f)
            }
        }
    }

    private fun drawPingBadge(ping: Long, x: Float, y: Float, w: Float) {
        val h = 22f
        val color = pingColor(ping)

        Draw2D.roundedRect(x, y, w, h, h * 0.5f, white(0.05f))
        Draw2D.roundedOutline(x, y, w, h, h * 0.5f, white(0.11f), 1f)

        val bars = when {
            ping < 0 -> 0
            ping < 60L -> 4
            ping < 130L -> 3
            ping < 240L -> 2
            else -> 1
        }
        var bx = x + 11f
        for (i in 0 until 4) {
            val barH = 3f + i * 2.6f
            val by = y + h - 6.5f - barH
            Draw2D.roundedRect(bx, by, 2.2f, barH, 1.1f, if (i < bars) color else white(0.16f))
            bx += 4.0f
        }

        val pingStr = if (ping < 0) "..." else "${ping}ms"
        NanoVGRenderer.drawRightVerticallyCenteredString(pingStr, x + w - 10f, y + h * 0.5f, color, 10.5f)
    }

    companion object {
        private val PING_EXECUTOR = Executors.newFixedThreadPool(6) { r ->
            Thread(r).apply { isDaemon = true; name = "Nocturne-Server-Ping" }
        }
    }

    private fun ensurePinged(entry: ServerSelectionList.OnlineServerEntry, data: ServerData, r: VanillaListScreenRenderer) {
        if (data.state() != ServerData.State.INITIAL) return
        val jpScreen = r.screen as? JoinMultiplayerScreen ?: return

        data.setState(ServerData.State.PINGING)
        data.motd = CommonComponents.EMPTY
        data.status = CommonComponents.EMPTY

        val mc = Minecraft.getInstance()
        val onSuccess = Runnable {
            mc.execute {
                data.setState(
                    if (data.protocol == SharedConstants.getCurrentVersion().protocolVersion()) {
                        ServerData.State.SUCCESSFUL
                    } else {
                        ServerData.State.INCOMPATIBLE
                    }
                )
            }
        }
        val onFailure = Runnable {
            mc.execute { data.setState(ServerData.State.UNREACHABLE) }
        }

        PING_EXECUTOR.execute {
            try {
                jpScreen.pinger.pingServer(
                    data, onSuccess, onFailure,
                    EventLoopGroupHolder.remote(mc.options.useNativeTransport())
                )
            } catch (_: UnknownHostException) {
                data.setState(ServerData.State.UNREACHABLE)
                data.motd = Component.translatable("multiplayer.status.cannot_resolve")
            } catch (_: Exception) {
                data.setState(ServerData.State.UNREACHABLE)
                data.motd = Component.translatable("multiplayer.status.cannot_connect")
            }
        }
    }
}
