package cn.reverie.render.hud.impl

import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.render.NotificationModule
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.MotionBlurHelper
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudRenderer
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import cn.reverie.render.ui.notification.NotificationManager
import cn.reverie.render.ui.notification.ToastType

class NotificationWidget : HudElement(
    name = "Notification",
    defaultX = 360f,
    defaultY = 380f,
    width = 240f,
    height = 26f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.CENTER,
    verticalAnchor = HudVerticalAnchor.CENTER
) {
    private var oscPhase = 0f
    private var initializedPosition = false

    override fun resetPosition() {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        x = (screenW - width) * 0.5f
        y = (screenH - height) * 0.78f
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val font: FontRenderer = RenderEngine.fontRenderer
        val bgAlpha = HUDModule.bgOpacity.getFloat()
        val textShadow = HUDModule.textShadow.value

        oscPhase = (oscPhase + 0.08f) % (PI.toFloat() * 2f)

        if (!initializedPosition && defaultX == x && defaultY == y) {
            x = (screenW - width) * 0.5f
            y = (screenH - height) * 0.78f
            initializedPosition = true
        }

        if (isEditing) {
            renderPreviewCard(x, y, width, height, font, bgAlpha, textShadow)
            return
        }

        val maxVisible = if (NotificationModule.enabled) NotificationModule.maxStack.getInt() else 5
        val activeList = NotificationManager.activeToasts.take(maxVisible)
        if (activeList.isEmpty()) return

        val now = System.currentTimeMillis()
        var currentSlotY = y
        val cardCenterX = x + width * 0.5f
        val animSpeed = if (NotificationModule.enabled) NotificationModule.animSpeed.getFloat() else 1.0f
        val isAutoWidth = if (NotificationModule.enabled) NotificationModule.autoWidth.value else true

        for (toast in activeList) {
            toast.tickLifetime()
            if (toast.isExpired) continue

            val tagText = when (toast.type) {
                ToastType.MODULE_ON, ToastType.MODULE_OFF -> "STATE"
                ToastType.SUCCESS -> "SUCCESS"
                ToastType.WARNING -> "WARN"
                ToastType.ERROR -> "ERROR"
                else -> "INFO"
            }
            val titleW = font.getStringWidth(toast.title, 10.5f)
            val tagW = font.getStringWidth(tagText, 8.5f)

            val maxAllowedScreenW = (screenW - 40f).coerceAtMost(460f)
            val targetContentWidth = if (isAutoWidth) {
                toast.calculateTargetWidth(font, maxAllowedScreenW)
            } else {
                width
            }

            toast.targetY = currentSlotY
            if (toast.currentY.isNaN()) {
                toast.currentY = currentSlotY - 10f
            }
            val deltaY = toast.targetY - toast.currentY
            if (abs(deltaY) < 0.05f) {
                toast.currentY = toast.targetY
            } else {
                toast.currentY += deltaY * (0.22f * animSpeed).coerceIn(0.10f, 0.45f)
            }

            val vy = if (toast.prevRenderY.isNaN()) 0f else (toast.currentY - toast.prevRenderY) * 50f
            toast.prevRenderY = toast.currentY

            val elapsed = toast.getElapsedMs()
            val exitAlpha = if (toast.isDismissed) {
                val dismissElapsed = if (toast.dismissStartTime > 0L) now - toast.dismissStartTime else 260L
                (1f - (dismissElapsed.toFloat() / 260f)).coerceIn(0f, 1f)
            } else 1.0f

            if (exitAlpha <= 0.001f) {
                toast.isExpired = true
                continue
            }

            val toastCx = cardCenterX + toast.dragOffsetX
            val toastCy = toast.currentY + 13f

            val CIRCLE_DURATION = 750L
            val EXPAND_DURATION = 400L
            val baseR = 12.5f

            if (elapsed < CIRCLE_DURATION) {

                val sweepTau = (elapsed.toFloat() / 300f).coerceIn(0f, 1f)
                val sweepAngle = sweepTau * 2f * PI.toFloat()

                if (sweepTau < 1.0f) {
                    MotionBlurHelper.renderMotionBlur(
                        toastCx - baseR, toastCy - baseR, baseR * 2f, baseR * 2f, baseR,
                        vx = 0f, vy = -3f * (1f - sweepTau),
                        color = toast.type.accentColor.copy(a = 0.20f * sweepTau * exitAlpha),
                        maxBlur = 10f
                    )
                } else if (abs(vy) > 0.4f) {
                    MotionBlurHelper.renderVerticalMotionBlur(
                        toastCx - baseR, toast.currentY, baseR * 2f, 26f, baseR,
                        vy,
                        color = NanoVGColor(1f, 1f, 1f, 0.18f * exitAlpha),
                        maxBlur = 12f
                    )
                }

                val iconAlpha = ((elapsed - 280f) / 180f).coerceIn(0f, 1f)

                drawOscilloscopeCircle(
                    toastCx, toastCy, baseR, sweepAngle, oscPhase,
                    toast.type, iconAlpha, toast.category, exitAlpha
                )

                toast.currentRenderWidth = 26f
                toast.lastRenderX = toastCx - baseR
                toast.lastRenderY = toast.currentY
                toast.lastWidth = baseR * 2f
                toast.lastHeight = 26f
                toast.prevRenderWidth = 26f

                currentSlotY += 26f + 6f
            } else {

                val expRaw = if (elapsed < CIRCLE_DURATION + EXPAND_DURATION) {
                    ((elapsed - CIRCLE_DURATION).toFloat() / EXPAND_DURATION).coerceIn(0f, 1f)
                } else 1.0f

                val exp = if (expRaw < 1.0f) CubicBezier(0.16f, 1.20f, 0.28f, 1.0f)(expRaw) else 1.0f
                val pulse = toast.pulseAnim.value

                if (toast.currentRenderWidth.isNaN()) {
                    toast.currentRenderWidth = 26f
                }

                if (expRaw < 1.0f) {
                    toast.currentRenderWidth = 26f + (targetContentWidth - 26f) * exp
                } else {
                    val deltaW = targetContentWidth - toast.currentRenderWidth
                    if (abs(deltaW) < 0.08f) {
                        toast.currentRenderWidth = targetContentWidth
                    } else {
                        val factor = (0.22f * animSpeed).coerceIn(0.10f, 0.45f)
                        toast.currentRenderWidth += deltaW * factor
                    }
                }

                val curW = toast.currentRenderWidth * pulse
                val curH = 26f * pulse
                val curR = 13f + (5.5f - 13f) * exp.coerceIn(0f, 1f)
                var curX = toastCx - curW * 0.5f
                curX = curX.coerceIn(8f, screenW - curW - 8f)
                val curY = toast.currentY

                val vx = (curW - toast.prevRenderWidth) * 35f
                toast.prevRenderWidth = curW
                if (abs(vx) > 0.4f) {
                    MotionBlurHelper.renderHorizontalMotionBlur(
                        curX, curY, curW, curH, curR,
                        vx,
                        color = NanoVGColor(1f, 1f, 1f, 0.22f * expRaw * exitAlpha),
                        maxBlur = 16f
                    )
                }

                if (abs(vy) > 0.4f) {
                    MotionBlurHelper.renderVerticalMotionBlur(
                        curX, curY, curW, curH, curR,
                        vy,
                        color = NanoVGColor(1f, 1f, 1f, 0.18f * exitAlpha),
                        maxBlur = 14f
                    )
                }

                drawWidgetShadow(curX, curY, curW, curH, curR, 0.45f * expRaw * exitAlpha, 8f, colorOverride = toast.type.accentColor)

                drawBackdrop(curX, curY, curW, curH, curR, NanoVGColor(0.035f, 0.04f, 0.055f, bgAlpha * expRaw * exitAlpha))

                val borderA = (0.08f + expRaw * 0.08f) * exitAlpha
                NanoVGRenderer.drawRoundedOutline(curX, curY, curW, curH, curR, NanoVGColor(1f, 1f, 1f, borderA), 0.75f)

                val badgeCx = curX + 13f
                val badgeCy = curY + curH * 0.5f
                val badgeR = 9.5f

                drawOscilloscopeRing(badgeCx, badgeCy, badgeR, 2f * PI.toFloat(), oscPhase, toast.type.accentColor, expRaw * 0.60f * exitAlpha)

                val iconSize = 13f
                val iconA = 0.95f * expRaw * exitAlpha
                if (toast.category != null) {
                    CategoryVectorIcons.drawCategoryIcon(
                        toast.category,
                        badgeCx - iconSize * 0.5f, badgeCy - iconSize * 0.5f, iconSize,
                        toast.type.accentColor.copy(a = iconA)
                    )
                } else {
                    when (toast.type) {
                        ToastType.SUCCESS, ToastType.MODULE_ON -> CategoryVectorIcons.drawCheckmark(
                            badgeCx - iconSize * 0.5f, badgeCy - iconSize * 0.5f, iconSize,
                            toast.type.accentColor.copy(a = iconA)
                        )
                        ToastType.ERROR, ToastType.MODULE_OFF -> CategoryVectorIcons.drawClearIcon(
                            badgeCx - iconSize * 0.5f, badgeCy - iconSize * 0.5f, iconSize,
                            toast.type.accentColor.copy(a = iconA)
                        )
                        else -> Draw2D.circle(badgeCx, badgeCy, 2.5f, toast.type.accentColor.copy(a = iconA))
                    }
                }

                val textBlur = if (expRaw < 1.0f) MotionBlurHelper.getBlurForProgress(expRaw, maxBlur = 6.0f) else 0f
                val textA = expRaw * exitAlpha
                var textX = curX + 26f

                font.drawString(
                    toast.title,
                    textX,
                    toastCy - 5f,
                    NanoVGColor.WHITE.copy(a = 0.98f * textA),
                    10.5f,
                    shadow = textShadow,
                    blur = textBlur
                )
                textX += titleW + 8f

                Draw2D.line(textX, curY + 6f, textX, curY + curH - 6f, NanoVGColor(1f, 1f, 1f, 0.09f * textA), 1.0f)
                textX += 8f

                font.drawString(
                    tagText,
                    textX,
                    toastCy - 4f,
                    NanoVGColor(0.65f, 0.68f, 0.76f, 0.85f * textA),
                    8.5f,
                    shadow = textShadow,
                    blur = textBlur
                )
                textX += tagW + 4f

                val hasClose = toast.isHovered || toast.hoverAnim.value > 0.05f
                val closeReserve = if (hasClose) 18f else 0f
                val repeatReserve = if (toast.repeatCount > 1) {
                    font.getStringWidth("×${toast.repeatCount}", 8.5f) + 14f
                } else 0f

                if (toast.message.isNotBlank()) {
                    val maxAllowedW = (curX + curW - textX - repeatReserve - closeReserve - 10f).coerceAtLeast(20f)
                    val trimmedMsg = font.trimWithEllipsis(toast.message, maxAllowedW, 10.0f)
                    val msgColor = if (toast.type == ToastType.MODULE_ON) {
                        NanoVGColor(0.85f, 0.98f, 0.90f, 0.95f * textA)
                    } else {
                        NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f * textA)
                    }
                    font.drawString(
                        trimmedMsg,
                        textX,
                        toastCy - 5f,
                        msgColor,
                        10.0f,
                        shadow = textShadow,
                        blur = textBlur
                    )
                }

                if (toast.repeatCount > 1) {
                    val badgeText = "×${toast.repeatCount}"
                    val bw = font.getStringWidth(badgeText, 8.5f) + 6f
                    val bh = 12f
                    val rightOffset = if (hasClose) 22f else 8f
                    val bx = curX + curW - bw - rightOffset
                    val by = toastCy - bh * 0.5f
                    Draw2D.roundedRect(bx, by, bw, bh, 3.5f, NanoVGColor(1f, 1f, 1f, 0.12f * textA))
                    Draw2D.roundedOutline(bx, by, bw, bh, 3.5f, NanoVGColor(1f, 1f, 1f, 0.16f * textA), 0.75f)
                    font.drawCenteredString(
                        badgeText, bx + bw * 0.5f, toastCy,
                        NanoVGColor(1f, 1f, 1f, 0.95f * textA), 8.5f, shadow = textShadow
                    )
                }

                if (hasClose) {
                    val hA = toast.hoverAnim.value * exitAlpha
                    val closeSize = 10f
                    val closeX = curX + curW - closeSize - 8f
                    val closeY = toastCy - closeSize * 0.5f
                    CategoryVectorIcons.drawClearIcon(
                        closeX, closeY, closeSize,
                        NanoVGColor(1f, 1f, 1f, (0.50f + toast.hoverAnim.value * 0.45f) * hA)
                    )
                }

                if (expRaw >= 0.98f) {
                    val progress = toast.getProgress()
                    val barPad = 6f
                    val maxBarW = curW - barPad * 2f
                    val barW = maxBarW * progress
                    val barH = 1.0f
                    val barY = curY + curH - 1.6f

                    Draw2D.roundedRect(curX + barPad, barY, maxBarW, barH, 0.5f, NanoVGColor(1f, 1f, 1f, 0.06f * textA))
                    if (barW > 0.5f) {
                        val progressColor = if (toast.isHovered) NanoVGColor(1f, 1f, 1f, 0.65f * textA) else toast.type.accentColor.copy(a = 0.55f * textA)
                        Draw2D.roundedRect(curX + barPad, barY, barW, barH, 0.5f, progressColor)
                    }
                }

                toast.lastRenderX = curX
                toast.lastRenderY = curY
                toast.lastWidth = curW
                toast.lastHeight = curH

                currentSlotY += curH + 6f
            }
        }
    }

    private fun drawOscilloscopeCircle(
        cx: Float, cy: Float,
        baseR: Float,
        sweepAngle: Float,
        wavePhase: Float,
        type: ToastType,
        iconAlpha: Float,
        category: Category?,
        exitAlpha: Float
    ) {

        if (HUDModule.widgetShadow.value && HUDModule.shadowStrength.getFloat() > 0.001f) {
            Draw2D.circle(cx, cy + 1f, baseR + 3f, NanoVGColor(0f, 0f, 0f, 0.35f * exitAlpha * HUDModule.shadowStrength.getFloat()))
        }
        val circleBgAlpha = (HUDModule.bgOpacity.getFloat() * exitAlpha).coerceIn(0f, 1f)
        Draw2D.circle(cx, cy, baseR, NanoVGColor(0.035f, 0.04f, 0.055f, circleBgAlpha))

        drawOscilloscopeRing(cx, cy, baseR, sweepAngle, wavePhase, type.accentColor, exitAlpha)

        val gridA = 0.16f * exitAlpha
        Draw2D.line(cx - 5f, cy, cx + 5f, cy, NanoVGColor(1f, 1f, 1f, gridA), 0.75f)
        Draw2D.line(cx, cy - 5f, cx, cy + 5f, NanoVGColor(1f, 1f, 1f, gridA), 0.75f)

        if (iconAlpha > 0.05f) {
            val totalIconA = iconAlpha * exitAlpha
            Draw2D.circle(cx, cy, 6.5f, type.accentColor.copy(a = 0.18f * totalIconA))

            val iconSize = 13f
            val ix = cx - iconSize * 0.5f
            val iy = cy - iconSize * 0.5f

            if (category != null) {
                CategoryVectorIcons.drawCategoryIcon(category, ix, iy, iconSize, type.accentColor.copy(a = 0.95f * totalIconA))
            } else {
                when (type) {
                    ToastType.SUCCESS, ToastType.MODULE_ON -> CategoryVectorIcons.drawCheckmark(
                        ix, iy, iconSize, type.accentColor.copy(a = 0.95f * totalIconA)
                    )
                    ToastType.ERROR, ToastType.MODULE_OFF -> CategoryVectorIcons.drawClearIcon(
                        ix, iy, iconSize, type.accentColor.copy(a = 0.95f * totalIconA)
                    )
                    else -> {
                        val font = RenderEngine.fontRenderer
                        font.drawCenteredString(type.symbol, cx, cy, type.accentColor.copy(a = 0.95f * totalIconA), 10.5f, shadow = textShadow)
                    }
                }
            }
        }
    }

    private fun drawOscilloscopeRing(
        cx: Float, cy: Float,
        baseR: Float,
        sweepAngle: Float,
        wavePhase: Float,
        color: NanoVGColor,
        alpha: Float
    ) {
        val steps = 36
        val stepRad = (2f * PI.toFloat()) / steps
        val sweep = sweepAngle.coerceIn(0f, 2f * PI.toFloat())
        val startAngle = -PI.toFloat() * 0.5f

        var prevX = Float.NaN
        var prevY = Float.NaN

        var a = 0f
        while (a <= sweep + 0.001f) {
            val curAngle = startAngle + a
            val wave = 1.3f * sin(5f * curAngle + wavePhase) * sin(2.5f * curAngle)
            val r = baseR + wave
            val px = cx + cos(curAngle) * r
            val py = cy + sin(curAngle) * r

            if (!prevX.isNaN()) {

                Draw2D.line(prevX, prevY, px, py, color.copy(a = 0.28f * alpha), 2.8f)

                Draw2D.line(prevX, prevY, px, py, NanoVGColor(1f, 1f, 1f, 0.95f * alpha), 1.1f)
            }

            prevX = px
            prevY = py
            a += stepRad
        }

        if (sweep in 0.05f..(2f * PI.toFloat() - 0.05f)) {
            val headAngle = startAngle + sweep
            val headWave = 1.3f * sin(5f * headAngle + wavePhase) * sin(2.5f * headAngle)
            val headR = baseR + headWave
            val hx = cx + cos(headAngle) * headR
            val hy = cy + sin(headAngle) * headR

            Draw2D.circle(hx, hy, 4.0f, color.copy(a = 0.35f * alpha))
            Draw2D.circle(hx, hy, 1.8f, NanoVGColor(1f, 1f, 1f, 0.98f * alpha))
        }
    }

    private fun renderPreviewCard(
        rx: Float, ry: Float, w: Float, h: Float,
        font: FontRenderer, bgAlpha: Float, textShadow: Boolean
    ) {
        val title = "Notification"
        val tag = "HUD"
        val msg = "Downward Stacking Active"

        val titleW = font.getStringWidth(title, 10.5f)
        val tagW = font.getStringWidth(tag, 8.5f)
        val msgW = font.getStringWidth(msg, 10.0f)
        val naturalW = 26f + titleW + 17f + tagW + 4f + msgW + 14f

        val previewW = if (NotificationModule.enabled && NotificationModule.autoWidth.value) {
            naturalW.coerceIn(150f, 440f)
        } else w
        width = previewW

        val radius = 5.5f
        val centerY = ry + h * 0.5f

        drawWidgetShadow(rx, ry, previewW, h, radius, 0.45f, blur = 8f)
        drawBackdrop(rx, ry, previewW, h, radius, NanoVGColor(0.035f, 0.04f, 0.055f, bgAlpha))
        NanoVGRenderer.drawRoundedOutline(rx, ry, previewW, h, radius, NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)

        val badgeCx = rx + 13f
        val badgeCy = centerY
        drawOscilloscopeRing(badgeCx, badgeCy, 9.5f, 2f * PI.toFloat(), oscPhase, NanoVGColor(0.20f, 0.85f, 0.55f), 0.80f)
        CategoryVectorIcons.drawCheckmark(badgeCx - 6.5f, badgeCy - 6.5f, 13f, NanoVGColor(0.20f, 0.85f, 0.55f, 0.95f))

        var curX = rx + 26f

        font.drawString(title, curX, centerY - 5f, NanoVGColor.WHITE, 10.5f, shadow = textShadow)
        curX += titleW + 8f

        Draw2D.line(curX, ry + 6f, curX, ry + h - 6f, NanoVGColor(1f, 1f, 1f, 0.09f), 1.0f)
        curX += 8f

        font.drawString(tag, curX, centerY - 4f, NanoVGColor(0.65f, 0.68f, 0.76f, 0.85f), 8.5f, shadow = textShadow)
        curX += tagW + 4f

        font.drawString(msg, curX, centerY - 5f, NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f), 10.0f, shadow = textShadow)

        Draw2D.roundedRect(rx + 6f, ry + h - 1.6f, previewW - 12f, 1.0f, 0.5f, NanoVGColor(0.20f, 0.85f, 0.55f, 0.55f))
    }
}
