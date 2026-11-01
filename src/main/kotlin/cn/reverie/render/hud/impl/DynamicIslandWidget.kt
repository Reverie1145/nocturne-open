package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.world.Scaffold
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.animation.MotionBlurHelper
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGTransform
import net.minecraft.client.Minecraft
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class DynamicIslandWidget : HudElement(
    name = "Dynamic Island",
    defaultX = 10f,
    defaultY = 6f,
    width = STATUS_W,
    height = STATUS_H,
    defaultEnabled = false,
    horizontalAnchor = HudHorizontalAnchor.CENTER
) {

    val showUser = BooleanSetting("Show User", "Display player username", true)
    val showServer = BooleanSetting("Show Server", "Display ping and server host", true)
    val showFps = BooleanSetting("Show FPS", "Display frames per second", true)
    val duration = NumberSetting("Duration", "Notification display time in seconds", 3.0, 1.0, 10.0, 0.5)

    init {
        addSetting(showUser)
        addSetting(showServer)
        addSetting(showFps)
        addSetting(duration)
        instance = this
    }

    private data class ToggleInfo(val name: String, val state: Boolean)

    private enum class Phase { STATUS, NARROW, STRETCH, OPEN, SHRINK, WIDEN }

    private val toggles = ArrayDeque<ToggleInfo>()
    private var phase = Phase.STATUS
    private var phaseAt = 0L
    private var lastToggleAt = 0L
    private var pulseAt = 0L
    private var statsMode = false
    private var lastW = STATUS_W
    private var lastH = STATUS_H
    private var anchored = false
    private var centerX = 0f

    override fun onCanvasResized(widthDelta: Float, heightDelta: Float) {
        if (anchored) centerX += widthDelta * horizontalAnchor.resizeFactor
    }

    private val wAnim = FloatAnimation(STATUS_W, STATUS_W, MORPH_MS, CubicBezier(0.16f, 1f, 0.3f, 1f))
    private val hAnim = FloatAnimation(STATUS_H, STATUS_H, MORPH_MS, CubicBezier(0.18f, 0.9f, 0.2f, 1.25f))
    private val statusAnim = FloatAnimation(1f, 1f, 200L)
    private val moduleAnim = FloatAnimation(0f, 0f, 200L)
    private val backdropBlurAnim = FloatAnimation(0f, 0f, MORPH_MS, CubicBezier(0.16f, 1f, 0.3f, 1f))
    private val knobAnim1 = FloatAnimation(0f, 0f, 300L, CubicBezier.EASE_OUT_BACK)
    private val knobAnim2 = FloatAnimation(0f, 0f, 300L, CubicBezier.EASE_OUT_BACK)

    private fun durationMs(): Long = (duration.getDouble() * 1000.0).toLong()

    private fun expandHeight(): Float = if (toggles.size > 1) EXPAND_DOUBLE_H else EXPAND_SINGLE_H

    private fun expandedTargetHeight(): Float = if (statsMode) STATS_H else expandHeight()

    private fun startNarrow(now: Long) {
        phase = Phase.NARROW
        phaseAt = now
        wAnim.animateTo(NARROW_W)
        statusAnim.animateTo(0f)
    }

    private fun startStretch(now: Long) {
        phase = Phase.STRETCH
        phaseAt = now
        wAnim.animateTo(EXPAND_W)
        hAnim.animateTo(expandedTargetHeight())
        moduleAnim.animateTo(1f)
        backdropBlurAnim.animateTo(if (statsMode) 1f else 0f)
    }

    private fun startCollapse(now: Long) {
        phase = Phase.SHRINK
        phaseAt = now
        moduleAnim.animateTo(0f)
        hAnim.animateTo(STATUS_H)
        backdropBlurAnim.animateTo(0f)
    }

    private fun startWiden(now: Long) {
        phase = Phase.WIDEN
        phaseAt = now
        wAnim.animateTo(STATUS_W)
        statusAnim.animateTo(1f)
        backdropBlurAnim.animateTo(0f)
    }

    private fun onToggled(module: Module) {
        if (!enabled) return
        val now = System.currentTimeMillis()

        if (module === Scaffold) {
            if (module.enabled) {
                statsMode = true
                toggles.clear()
                lastToggleAt = now
                when (phase) {
                    Phase.STATUS -> startNarrow(now)
                    Phase.STRETCH, Phase.OPEN -> {
                        pulseAt = now
                        hAnim.animateTo(STATS_H)
                        backdropBlurAnim.animateTo(1f)
                    }
                    Phase.SHRINK, Phase.WIDEN -> {
                        startNarrow(now)
                        moduleAnim.animateTo(0f)
                    }
                    else -> {}
                }
                return
            }
            statsMode = false
        }

        if (statsMode) {
            pulseAt = now
            return
        }

        val had = toggles.any { it.name == module.name }
        toggles.removeAll { it.name == module.name }
        toggles.addFirst(ToggleInfo(module.name, module.enabled))
        if (!had) {
            while (toggles.size > 2) toggles.removeLast()
        }
        lastToggleAt = now
        when (phase) {
            Phase.STATUS -> startNarrow(now)
            Phase.OPEN -> {
                pulseAt = now
                hAnim.animateTo(expandHeight())
            }
            Phase.STRETCH -> {
                pulseAt = now
                hAnim.animateTo(expandHeight())
            }
            Phase.SHRINK, Phase.WIDEN -> {
                startNarrow(now)
                moduleAnim.animateTo(0f)
            }
            else -> {}
        }
    }

    override fun onDisable() {
        phase = Phase.STATUS
        statsMode = false
        toggles.clear()
        wAnim.setImmediate(STATUS_W)
        hAnim.setImmediate(STATUS_H)
        statusAnim.setImmediate(1f)
        moduleAnim.setImmediate(0f)
        backdropBlurAnim.setImmediate(0f)
        knobAnim1.setImmediate(0f)
        knobAnim2.setImmediate(0f)
    }

    override fun onTick() {
        super.onTick()
        val now = System.currentTimeMillis()
        when (phase) {
            Phase.NARROW -> if (now - phaseAt >= (MORPH_MS * 0.45f).toLong()) startStretch(now)
            Phase.STRETCH, Phase.OPEN -> {
                if (!statsMode && now - lastToggleAt >= durationMs()) startCollapse(now)
            }
            Phase.SHRINK -> if (now - phaseAt >= (MORPH_MS * 0.42f).toLong()) startWiden(now)
            Phase.WIDEN -> if (now - phaseAt >= MORPH_MS) {
                phase = Phase.STATUS
                toggles.clear()
            }
            else -> {}
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return
        if (!anchored) {
            centerX = ResolutionHelper.scaledWidth * 0.5f
            y = 6f
            anchored = true
        }

        val now = System.currentTimeMillis()
        if (phase == Phase.STATUS || phase == Phase.WIDEN) {
            val tw = statusTargetWidth()
            if (abs(wAnim.targetValue - tw) > 0.5f) wAnim.animateTo(tw)
        }
        val pulse = run {
            val p = (now - pulseAt) / 160f
            if (p in 0f..1f) 1f - 0.03f * sin((p * Math.PI).toFloat()) else 1f
        }
        val cw = wAnim.value * pulse
        val ch = hAnim.value * pulse
        width = cw
        height = ch
        if (isDragging) {
            centerX = x + cw * scale * 0.5f
        } else {
            x = centerX - cw * scale * 0.5f
        }

        val glassAlpha = if (isEditing && !this.enabled) 0.55f else 1f

        val vx = cw - lastW
        val vy = ch - lastH
        lastW = cw
        lastH = ch
        val r = min(ch * 0.5f, 19f)
        if (abs(vx) > 0.05f) {
            MotionBlurHelper.renderHorizontalMotionBlur(x, y, cw, ch, r, vx,
                NanoVGColor(1f, 1f, 1f, 0.30f * glassAlpha), maxBlur = 14f)
        }
        if (abs(vy) > 0.05f) {
            MotionBlurHelper.renderVerticalMotionBlur(x, y, cw, ch, r, vy,
                NanoVGColor(1f, 1f, 1f, 0.30f * glassAlpha), maxBlur = 14f)
        }

        drawIslandBody(cw, ch, glassAlpha)

        val sa = statusAnim.value
        if (sa > 0.01f) {
            val t = NanoVGTransform(Draw2D.context)
            t.save()
            t.translate(centerX, y + ch * 0.5f)
            t.scale(0.93f + 0.07f * sa)
            t.translate(-centerX, -(y + ch * 0.5f))
            drawStatusLayer(sa, MotionBlurHelper.getBlurForProgress(sa, maxBlur = 5.5f))
            t.restore()
        }

        val ma = moduleAnim.value
        if (ma > 0.01f) {
            val t = NanoVGTransform(Draw2D.context)
            t.save()
            t.translate(centerX, y + ch * 0.5f)
            t.translate(0f, 4f * (1f - ma))
            t.scale(0.93f + 0.07f * ma)
            t.translate(-centerX, -(y + ch * 0.5f))
            drawModuleOrStatsLayer(ma, MotionBlurHelper.getBlurForProgress(ma, maxBlur = 5.5f))
            t.restore()
        }

        if (isEditing) renderEditBoundingBox(partialTicks)
    }

    private fun drawIslandBody(w: Float, h: Float, alpha: Float) {
        val r = min(h * 0.5f, 19f)
        if (isWidgetShadowEnabled) {
            Draw2D.shadow(x, y, w, h, r, 22f, NanoVGColor(0f, 0f, 0f, 0.55f * shadowStrength * alpha), 0f)
            Draw2D.shadow(x, y, w, h, r, 8f, NanoVGColor(0f, 0f, 0f, 0.30f * shadowStrength * alpha), 0f)
        }
        if (moduleAnim.value > 0.05f) {
            Draw2D.shadow(x, y, w, h, r, 30f,
                LAVENDER.withAlpha(0.18f * moduleAnim.value * alpha), 0f)
        }
        drawBackdrop(x, y, w, h, r, NanoVGColor(0f, 0f, 0f, bgOpacity),
            blurRadius = HUDModule.blurRadius.getFloat() * backdropBlurAnim.value)
        Draw2D.roundedOutline(x, y, w, h, r, NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 1f)
    }

    private fun statusTargetWidth(): Float {
        val font = RenderEngine.fontRenderer
        val mc = Minecraft.getInstance()
        val fs = 11.5f
        var content = HudVectorIcons.faWidth(11f, 384f) + 6.5f + font.getStringWidth("Nocturne", fs)
        var groups = 1
        if (showUser.value) {
            content += HudVectorIcons.faWidth(9f, 448f) + 5f + font.getStringWidth(mc.user.name, fs)
            groups++
        }
        if (showServer.value) {
            content += HudVectorIcons.faWidth(10f, 640f) + 5f +
                font.getStringWidth("${currentPing(mc)}ms", fs) +
                font.getStringWidth(" to ", fs) +
                font.getStringWidth(mc.currentServer?.ip ?: "Singleplayer", fs)
            groups++
        }
        if (showFps.value) {
            content += HudVectorIcons.faWidth(9f, 512f) + 5f + font.getStringWidth("${mc.fps} fps", fs)
            groups++
        }
        val sepW = font.getStringWidth(SEPARATOR, 9f)
        return 32f + content + (sepW + 10f) * (groups - 1)
    }

    private fun drawStatusLayer(alpha: Float, blur: Float) {
        val mc = Minecraft.getInstance()
        val font = RenderEngine.fontRenderer
        val shadow = HUDModule.textShadow.value
        val cy = y + height * 0.5f
        val padX = 16f
        val fs = 11.5f

        val textShadowBlur = if (blur > 0.05f) blur else 0f

        data class Group(val width: Float, val draw: (Float, Float) -> Unit)

        val groups = mutableListOf<Group>()

        run {
            val iconH = 11f
            val iconW = HudVectorIcons.faWidth(iconH, 384f)
            val label = "Nocturne"
            val labelW = font.getStringWidth(label, fs)
            val gw = iconW + 6.5f + labelW
            groups.add(Group(gw) { gx, gy ->
                val icx = gx + iconW * 0.5f
                val icy = gy
                val glowScales = floatArrayOf(1.9f, 1.45f)
                val glowAlphas = floatArrayOf(0.10f, 0.20f)
                for (i in glowScales.indices) {
                    val gh = iconH * glowScales[i]
                    HudVectorIcons.drawFa(HudVectorIcons.FA_MOON, icx - gh * 0.5f, icy - gh * 0.5f, gh,
                        LAVENDER.withAlpha(glowAlphas[i] * alpha), 384f)
                }
                HudVectorIcons.drawFa(HudVectorIcons.FA_MOON, gx, gy - iconH * 0.5f, iconH,
                    LAVENDER.withAlpha(alpha), 384f)
                font.drawString(label, gx + iconW + 6.5f, gy, TEXT_PRIMARY.multiplyAlpha(alpha), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
            })
        }

        if (showUser.value) {
            val iconH = 9f
            val iconW = HudVectorIcons.faWidth(iconH, 448f)
            val label = mc.user.name
            val labelW = font.getStringWidth(label, fs)
            val gw = iconW + 5f + labelW
            groups.add(Group(gw) { gx, gy ->
                HudVectorIcons.drawFa(HudVectorIcons.FA_USER, gx, gy - iconH * 0.5f, iconH,
                    TEXT_SOFT.multiplyAlpha(alpha), 448f)
                font.drawString(label, gx + iconW + 5f, gy, TEXT_PRIMARY.multiplyAlpha(alpha), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
            })
        }

        if (showServer.value) {
            val iconH = 10f
            val iconW = HudVectorIcons.faWidth(iconH, 640f)
            val ping = currentPing(mc)
            val pingText = "${ping}ms"
            val pingColor = if (ping < 120) LINK_GREEN else NanoVGColor(0.949f, 0.333f, 0.333f, 1f)
            val pingW = font.getStringWidth(pingText, fs)
            val toW = font.getStringWidth(" to ", fs)
            val host = mc.currentServer?.ip ?: "Singleplayer"
            val hostW = font.getStringWidth(host, fs)
            val gw = iconW + 5f + pingW + toW + hostW
            groups.add(Group(gw) { gx, gy ->
                val icx = gx + iconW * 0.5f
                val t = NanoVGTransform(Draw2D.context)
                t.save()
                t.translate(icx, gy)
                t.rotateDegrees(45f)
                t.translate(-icx, -gy)
                HudVectorIcons.drawFa(HudVectorIcons.FA_LINK, icx - iconW * 0.5f, gy - iconH * 0.5f, iconH,
                    LINK_GREEN.withAlpha(alpha), 640f)
                t.restore()
                var cxr = gx + iconW + 5f
                font.drawString(pingText, cxr, gy, pingColor.withAlpha(alpha), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
                cxr += pingW
                font.drawString(" to ", cxr, gy, TEXT_PRIMARY.multiplyAlpha(alpha * 0.95f), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
                cxr += toW
                font.drawString(host, cxr, gy, TEXT_PRIMARY.multiplyAlpha(alpha * 0.95f), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
            })
        }

        if (showFps.value) {
            val iconH = 9f
            val iconW = HudVectorIcons.faWidth(iconH, 512f)
            val label = "${mc.fps} fps"
            val labelW = font.getStringWidth(label, fs)
            val gw = iconW + 5f + labelW
            groups.add(Group(gw) { gx, gy ->
                HudVectorIcons.drawFa(HudVectorIcons.FA_ARROWS_ROTATE, gx, gy - iconH * 0.5f, iconH,
                    TEXT_PRIMARY.multiplyAlpha(alpha * 0.9f), 512f)
                font.drawString(label, gx + iconW + 5f, gy, TEXT_PRIMARY.multiplyAlpha(alpha), fs,
                    TextAlignment.MIDDLE_LEFT, shadow, textShadowBlur)
            })
        }

        if (groups.isEmpty()) return

        val sepFs = 9f
        val sepW = font.getStringWidth(SEPARATOR, sepFs)
        val gap = 10f

        var curX = x + padX
        for ((idx, group) in groups.withIndex()) {
            group.draw(curX, cy)
            curX += group.width
            if (idx < groups.size - 1) {
                font.drawString(SEPARATOR, curX + gap * 0.5f, cy,
                    NanoVGColor(1f, 1f, 1f, 0.40f * alpha), sepFs, TextAlignment.MIDDLE_CENTER, false, textShadowBlur)
                curX += gap + sepW
            }
        }
    }

    private fun drawModuleOrStatsLayer(alpha: Float, blur: Float) {
        if (statsMode && Scaffold.enabled) {
            drawStatsCard(alpha, blur)
        } else {
            drawModuleLayer(alpha, blur)
        }
    }

    private fun drawStatsCard(alpha: Float, blur: Float) {
        val font = RenderEngine.fontRenderer
        val shadow = HUDModule.textShadow.value
        val blurValue = if (blur > 0.05f) blur else 0f
        val left = x + 15f
        val right = x + width - 15f
        val top = y + 13f
        val iconSize = 14f
        val cubeOutline = "M12 3 L3.5 7.5 L3.5 16.5 L12 21 L20.5 16.5 L20.5 7.5 L12 3 M3.5 7.5 L12 12 L20.5 7.5 M12 12 V21"
        HudVectorIcons.draw(cubeOutline,
            left, top - iconSize * 0.5f, iconSize, NanoVGColor(0.62f, 0.78f, 1.00f, alpha), strokeWidth = 1.6f)
        font.drawString("SCAFFOLD", left + 20f, top, STATS_TITLE.multiplyAlpha(alpha), 8.5f,
            TextAlignment.MIDDLE_LEFT, shadow, blurValue)

        val rate = Scaffold.placementRate()
        val count = Scaffold.blocksLeft()
        val countText = count.toString()
        font.drawString(countText, left, y + 36f, TEXT_PRIMARY.multiplyAlpha(alpha), 18f,
            TextAlignment.MIDDLE_LEFT, shadow, blurValue)
        val countWidth = font.getStringWidth(countText, 18f)
        font.drawString("blocks left", left + countWidth + 6f, y + 37f,
            TEXT_SOFT.multiplyAlpha(alpha), 9.5f, TextAlignment.MIDDLE_LEFT, shadow, blurValue)

        val rateText = "${String.format(Locale.ROOT, "%.1f", rate)}/s"
        val badgeW = font.getStringWidth(rateText, 9f) + 16f
        val badgeH = 19f
        val badgeX = right - badgeW
        val badgeY = y + 27f
        Draw2D.roundedRect(badgeX, badgeY, badgeW, badgeH, 9.5f,
            NanoVGColor(0.30f, 0.54f, 0.42f, 0.30f * alpha))
        Draw2D.roundedOutline(badgeX, badgeY, badgeW, badgeH, 9.5f,
            NanoVGColor(0.42f, 0.83f, 0.62f, 0.24f * alpha), 0.8f)
        font.drawString(rateText, badgeX + badgeW * 0.5f, badgeY + badgeH * 0.5f,
            NanoVGColor(0.58f, 0.91f, 0.70f, alpha), 9f, TextAlignment.MIDDLE_CENTER, shadow, blurValue)

        val barX = left
        val barW = right - left
        val barY = y + height - 8f
        Draw2D.roundedRect(barX, barY, barW, 3f, 1.5f, NanoVGColor(1f, 1f, 1f, 0.13f * alpha))
        val frac = Scaffold.blocksRemainingFraction()
        if (frac > 0.005f) {
            Draw2D.roundedRect(barX, barY, (barW * frac).coerceAtLeast(3f), 3f, 1.5f,
                NanoVGColor(0.56f, 0.72f, 0.98f, 0.95f * alpha))
        }
    }

    private fun drawModuleLayer(alpha: Float, blur: Float) {
        val font = RenderEngine.fontRenderer
        val shadow = HUDModule.textShadow.value
        val blurValue = if (blur > 0.05f) blur else 0f

        val first = toggles.firstOrNull() ?: return
        knobAnim1.animateTo(if (first.state) 1f else 0f)
        val second = toggles.getOrNull(1)
        if (second != null) knobAnim2.animateTo(if (second.state) 1f else 0f)

        val swW = 38f
        val swH = 22f

        val singleRow = toggles.size <= 1
        val dividerCy = y + height * 0.5f
        val rowCy1 = if (singleRow) dividerCy else dividerCy - 22f
        val rowCy2 = dividerCy + 22f

        drawToggleRow(font, first, knobAnim1.value, rowCy1, swW, swH, alpha, blurValue, shadow, false)

        if (!singleRow && second != null) {
            Draw2D.line(x + 14f, dividerCy, x + width - 14f, dividerCy,
                NanoVGColor(1f, 1f, 1f, 0.10f * alpha), 1f)
            drawToggleRow(font, second, knobAnim2.value, rowCy2, swW, swH, alpha, blurValue, shadow, true)
        }
    }

    private fun drawToggleRow(
        font: cn.reverie.render.font.FontRenderer,
        info: ToggleInfo,
        knob: Float,
        rowCy: Float,
        swW: Float,
        swH: Float,
        alpha: Float,
        blur: Float,
        shadow: Boolean,
        secondRow: Boolean
    ) {
        val swX = x + 14f
        val swY = rowCy - swH * 0.5f

        val track = lerpColor(SWITCH_OFF_TRACK, SWITCH_ON_TRACK, knob).multiplyAlpha(alpha)
        Draw2D.roundedRect(swX, swY, swW, swH, swH * 0.5f, track)
        Draw2D.roundedOutline(swX, swY, swW, swH, swH * 0.5f,
            NanoVGColor(1f, 1f, 1f, (0.14f + 0.21f * knob) * alpha), 1.2f)

        val inset = 2.6f
        val knobR = swH * 0.5f - inset
        val knobX = swX + inset + knobR + (swW - 2f * (inset + knobR)) * knob
        val knobColor = lerpColor(SWITCH_OFF_KNOB, SWITCH_ON_KNOB, knob).multiplyAlpha(alpha)
        cn.reverie.render.nanovg.NanoVGRenderer.drawCircle(knobX, swY + swH * 0.5f, knobR, knobColor)

        val tx = swX + swW + 11f
        font.drawString("Module Toggled", tx, rowCy - 7.5f, TEXT_PRIMARY.multiplyAlpha(alpha), 11.5f,
            TextAlignment.MIDDLE_LEFT, shadow, blur)

        val stateText = if (info.state) "Enabled!" else "Disabled!"
        val hex = if (info.state) "#4ade4a" else "#f25555"
        font.drawString("${info.name} has been §$hex$stateText", tx, rowCy + 7.5f,
            TEXT_PRIMARY.multiplyAlpha(alpha * 0.95f), 10.5f, TextAlignment.MIDDLE_LEFT, shadow, blur)
    }

    private fun currentPing(mc: Minecraft): Int {
        return try {
            val player = mc.player
            if (player != null && mc.connection != null) {
                mc.connection!!.getPlayerInfo(player.uuid)?.latency?.coerceAtLeast(0) ?: 0
            } else 0
        } catch (_: Throwable) {
            0
        }
    }

    private fun lerpColor(c1: NanoVGColor, c2: NanoVGColor, t: Float): NanoVGColor = c1.lerp(c2, t)

    companion object {
        const val STATUS_W = 376f
        const val NARROW_W = 248f
        const val EXPAND_W = 276f
        const val STATUS_H = 32f
        const val EXPAND_SINGLE_H = 58f
        const val EXPAND_DOUBLE_H = 104f
        const val STATS_H = 64f
        const val MORPH_MS = 380L

        const val SEPARATOR = "•"

        val LAVENDER = NanoVGColor(0.616f, 0.686f, 0.980f, 1f)
        val LINK_GREEN = NanoVGColor(0.439f, 0.859f, 0.200f, 1f)
        val ENABLED_GREEN = NanoVGColor(0.290f, 0.871f, 0.290f, 1f)
        val DISABLED_RED = NanoVGColor(0.949f, 0.333f, 0.333f, 1f)
        val SWITCH_OFF_TRACK = NanoVGColor(0.169f, 0.188f, 0.227f, 1f)
        val SWITCH_OFF_KNOB = NanoVGColor(0.475f, 0.502f, 0.565f, 1f)
        val SWITCH_ON_TRACK = NanoVGColor(0.604f, 0.682f, 0.902f, 1f)
        val SWITCH_ON_KNOB = NanoVGColor(0.271f, 0.310f, 0.420f, 1f)

        val TEXT_PRIMARY = NanoVGColor(1f, 1f, 1f, 0.96f)
        val TEXT_SOFT = NanoVGColor(1f, 1f, 1f, 0.80f)
        val STATS_TITLE = NanoVGColor(0.62f, 0.74f, 0.94f, 1f)

        private var instance: DynamicIslandWidget? = null

        fun onModuleToggle(module: Module) {
            if (module is DynamicIslandWidget) return
            instance?.onToggled(module)
        }
    }
}
