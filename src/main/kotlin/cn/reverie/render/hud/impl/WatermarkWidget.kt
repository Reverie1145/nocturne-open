package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class WatermarkWidget : HudElement(
    name = "Watermark",
    defaultX = 10f,
    defaultY = 10f,
    width = 214f,
    height = 26f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.LEFT,
    verticalAnchor = HudVerticalAnchor.TOP
) {
    val showFPS = BooleanSetting("Show FPS", "Display current frames per second", true)
    val showPing = BooleanSetting("Show Ping", "Display network latency in ms", true)
    val showTime = BooleanSetting("Show Time", "Display real-world clock time", false)

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val textPrimary = NanoVGColor(1f, 1f, 1f, 0.98f)
    private val textSecondary = NanoVGColor(1f, 1f, 1f, 0.55f)
    private val dividerColor = NanoVGColor(1f, 1f, 1f, 0.08f)

    private val widthAnim = FloatAnimation(214f, 214f, 140L, CubicBezier.EASE_OUT)

    init {
        addSetting(showFPS)
        addSetting(showPing)
        addSetting(showTime)
    }

    private data class StatItem(val value: String, val unit: String)

    private fun collectStats(mc: Minecraft): List<StatItem> {
        val stats = mutableListOf<StatItem>()

        if (showFPS.value) {
            stats.add(StatItem("${mc.fps}", "fps"))
        }

        if (showPing.value) {
            val ping = try {
                val player = mc.player
                if (player != null && mc.connection != null) {
                    mc.connection!!.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
                } else 0
            } catch (_: Throwable) { 0 }
            stats.add(StatItem("$ping", "ms"))
        }

        if (showTime.value) {
            stats.add(StatItem(LocalTime.now().format(timeFormatter), ""))
        }

        return stats
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val mc = Minecraft.getInstance()
        val font: FontRenderer = RenderEngine.fontRenderer

        val h = 26f
        val radius = 8f
        val centerY = y + h * 0.5f
        val textShadow = HUDModule.textShadow.value
        val fadeMode = HUDModule.colorMode.isMode("Fade")
        val accent = HUDModule.sampleColor(0f)
        val accentEnd = if (fadeMode) HUDModule.sampleFadeColor(1f) else HUDModule.sampleColor(0.3f)

        val brand = "NOCTURNE"
        val brandFs = 9.8f
        val brandW = font.getStringWidth(brand, brandFs)
        val iconSize = 9.5f

        val pillPadX = 9.0f
        val pillH = 18.0f
        val pillW = pillPadX + iconSize + 5.0f + brandW + pillPadX
        val pillY = centerY - pillH * 0.5f
        val pillRadius = 6.0f

        val stats = collectStats(mc)
        val valFs = 10f
        val unitFs = 7.8f
        val statGap = 10f

        var targetW = 5f + pillW

        if (stats.isNotEmpty()) {
            targetW += 10f + 1f + 10f
            for ((idx, stat) in stats.withIndex()) {
                targetW += font.getStringWidth(stat.value, valFs)
                if (stat.unit.isNotEmpty()) targetW += 2.5f + font.getStringWidth(stat.unit, unitFs)
                if (idx < stats.size - 1) targetW += statGap
            }
        }
        targetW += 5f

        if (abs(widthAnim.value - targetW) < 0.2f) {
            widthAnim.setImmediate(targetW)
        } else {
            widthAnim.animateTo(targetW)
        }
        width = widthAnim.value
        height = h

        drawWidgetShadow(x, y, width, height, radius, 0.45f, blur = 10f)
        drawBackdrop(x, y, width, height, radius, NanoVGColor(0.03f, 0.034f, 0.045f, bgOpacity))
        NanoVGRenderer.drawRoundedOutline(x, y, width, height, radius, NanoVGColor(1f, 1f, 1f, 0.07f), 0.75f)

        val pillX = x + 5f
        Draw2D.glow(pillX, pillY, pillW, pillH, pillRadius, 7f, accent.withAlpha(0.35f))
        if (fadeMode) {
            Draw2D.gradientLinear(pillX, pillY, pillW, pillH, pillRadius, accent, accentEnd, horizontal = true)
        } else {
            Draw2D.gradientDiagonal(pillX, pillY, pillW, pillH, pillRadius, accent, accentEnd)
        }

        var curX = pillX + pillPadX
        HudVectorIcons.drawMoon(curX, centerY - iconSize * 0.5f, iconSize, NanoVGColor(1f, 1f, 1f, 0.95f))
        curX += iconSize + 5f
        font.drawString(brand, curX, centerY - 4.7f, textPrimary, brandFs, shadow = textShadow)

        if (stats.isNotEmpty()) {
            curX = pillX + pillW + 10f
            Draw2D.line(curX, y + 7f, curX, y + h - 7f, dividerColor, 0.75f)
            curX += 10f

            for ((idx, stat) in stats.withIndex()) {
                font.drawString(stat.value, curX, centerY - 4.8f, textPrimary, valFs, shadow = textShadow)
                curX += font.getStringWidth(stat.value, valFs)

                if (stat.unit.isNotEmpty()) {
                    font.drawString(stat.unit, curX + 2.5f, centerY - 3.7f, textSecondary, unitFs, shadow = textShadow)
                    curX += 2.5f + font.getStringWidth(stat.unit, unitFs)
                }

                if (idx < stats.size - 1) curX += statGap
            }
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}
