package cn.reverie.render.ui.notification
import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.module.misc.YuriGallery
import cn.reverie.render.animation.MotionBlurHelper
import cn.reverie.render.art.AnimeArt
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.ArcRenderer
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import kotlin.math.sin
    internal fun ToastNotification.renderDynamicIsland(
        screenWidth: Float,
        s: Float,
        pulse: Float,
        hover: Float
    ) {
        val font: FontRenderer = RenderEngine.fontRenderer
        val nvg = RenderEngine.nanoVG

        val titleW = font.getStringWidth(title, 11f)
        val msgW = if (message.isNotBlank()) font.getStringWidth(message, 10f) else 0f
        val hasMsg = message.isNotBlank()
        val contentW = titleW + (if (hasMsg) msgW + 16f else 0f) + (if (repeatCount > 1) 26f else 0f)
        val width = (contentW + 56f).coerceIn(150f, 440f) * pulse
        val height = 30f * pulse
        val x = (screenWidth - width) * 0.5f + dragOffsetX
        val y = currentY

        lastRenderX = x
        lastRenderY = y
        lastWidth = width
        lastHeight = height

        val radius = height * 0.5f

        val scaleX = (1f + 0.06f * sin(s * Math.PI.toFloat()) * (1f - s)) * pulse
        val scaleY = (0.7f + 0.3f * s) * pulse

        nvg.transform.save()
        val cx = x + width * 0.5f
        val cy = y + height * 0.5f
        nvg.transform.translate(cx, cy)
        nvg.transform.scale(scaleX, scaleY)
        nvg.transform.translate(-cx, -cy)

        Draw2D.shadow(x, y, width, height, radius, 14f, NanoVGColor(0f, 0f, 0f, 0.55f * s), 2f)
        Draw2D.roundedRect(x, y, width, height, radius, NanoVGColor(0.03f, 0.04f, 0.06f, 0.94f * s))

        val borderAlpha = (0.10f + hover * 0.12f) * s
        Draw2D.roundedOutline(x, y, width, height, radius, NanoVGColor(1f, 1f, 1f, borderAlpha), 0.85f)
        Draw2D.gradientLinear(
            x + 8f, y + 1f, width - 16f, 1.5f, 0.75f,
            NanoVGColor(1f, 1f, 1f, 0.16f * s),
            NanoVGColor(1f, 1f, 1f, 0.02f * s)
        )

        val iconSize = 14f
        val iconX = x + 8f
        val iconY = y + (height - iconSize) * 0.5f

        Draw2D.circle(iconX + iconSize * 0.5f, iconY + iconSize * 0.5f, 3.5f, type.accentColor.copy(a = 0.90f * s))
        Draw2D.circle(iconX + iconSize * 0.5f, iconY + iconSize * 0.5f, 7f, type.accentColor.copy(a = 0.18f * s))

        val textBlur = MotionBlurHelper.getBlurForProgress(s, maxBlur = 5.0f)
        val textX = iconX + iconSize + 6f
        val textY = y + height * 0.5f

        font.drawString(
            title, textX, textY,
            NanoVGColor(1f, 1f, 1f, 0.95f * s),
            11f, TextAlignment.MIDDLE_LEFT, shadow = false, blur = textBlur
        )

        var curOffset = textX + titleW + 5f
        if (hasMsg) {

            Draw2D.circle(curOffset + 2f, textY, 1.2f, NanoVGColor(1f, 1f, 1f, 0.25f * s))
            curOffset += 8f
            val maxAllowedW = width - (curOffset - x) - (if (repeatCount > 1) 40f else 26f)
            val trimmedMsg = font.trimWithEllipsis(message, maxAllowedW, 10f)
            font.drawString(
                trimmedMsg, curOffset, textY,
                NanoVGColor(0.65f, 0.65f, 0.70f, 0.90f * s),
                10f, TextAlignment.MIDDLE_LEFT, shadow = false, blur = textBlur
            )
            curOffset += font.getStringWidth(trimmedMsg, 10f) + 6f
        }

        if (repeatCount > 1) {
            val badgeText = "×$repeatCount"
            val badgeW = font.getStringWidth(badgeText, 8.5f) + 7f
            val badgeH = 12f
            val badgeX = x + width - badgeW - 20f
            val badgeY = y + (height - badgeH) * 0.5f
            Draw2D.roundedRect(badgeX, badgeY, badgeW, badgeH, 4f, NanoVGColor(1f, 1f, 1f, 0.12f * s))
            font.drawCenteredString(
                badgeText, badgeX + badgeW * 0.5f, badgeY + badgeH * 0.5f,
                NanoVGColor(1f, 1f, 1f, 0.95f * s), 8.5f, shadow = false
            )
        }

        val ringProgress = getProgress()
        val ringRadius = 5.5f
        val ringCx = x + width - 13f
        val ringCy = y + height * 0.5f
        ArcRenderer.drawProgressRing(
            RenderEngine.nanoVG.handle,
            ringCx, ringCy, ringRadius, ringProgress,
            trackColor = NanoVGColor(1f, 1f, 1f, 0.10f * s),
            fillColor = type.accentColor.copy(a = 0.85f * s),
            strokeWidth = 1.4f
        )

        if (hover > 0.05f) {
            CategoryVectorIcons.drawClearIcon(
                ringCx - 4f, ringCy - 4f, 8f,
                NanoVGColor(1f, 1f, 1f, hover * s)
            )
        }

        nvg.transform.restore()
    }


    internal fun ToastNotification.renderZincPrecision(
        anchorX: Float,
        y: Float,
        isAnchorLeft: Boolean,
        isAnchorTop: Boolean,
        s: Float,
        pulse: Float,
        hover: Float
    ) {
        val font: FontRenderer = RenderEngine.fontRenderer
        val h = 26f
        val radius = 5.5f
        val centerY = y + h * 0.5f

        val titleW = font.getStringWidth(title, 10.5f)
        val tagText = when (type) {
            ToastType.MODULE_ON, ToastType.MODULE_OFF -> "STATE"
            ToastType.SUCCESS -> "SUCCESS"
            ToastType.WARNING -> "WARN"
            ToastType.ERROR -> "ERROR"
            else -> "INFO"
        }
        val tagW = font.getStringWidth(tagText, 8.5f)
        val msgW = if (message.isNotBlank()) font.getStringWidth(message, 10.0f) else 0f
        val repeatW = if (repeatCount > 1) 26f else 0f

        val avatarId = when (type) {
            ToastType.SUCCESS, ToastType.MODULE_ON -> AnimeArt.TOAST_AVATAR_SUCCESS
            ToastType.WARNING -> AnimeArt.TOAST_AVATAR_WARNING
            ToastType.ERROR -> AnimeArt.TOAST_AVATAR_ERROR
            ToastType.INFO, ToastType.MODULE_OFF -> AnimeArt.TOAST_AVATAR_INFO
        }
        val useAvatar = YuriGallery.enabled && YuriGallery.toastAvatars.value && AnimeArt.image(avatarId) != null
        val iconSize = 14f
        val iconW = if (useAvatar) 17f + 7f else iconSize + 7f

        var contentW = 12f + iconW + titleW + 8f + 1f + 8f + tagW + 4f + msgW + repeatW + (if (hover > 0.05f) 14f else 0f) + 12f
        contentW = contentW.coerceIn(150f, 440f) * pulse
        val width = contentW
        val height = h * pulse

        val slideOffset = (1f - s) * (width + 30f) * (if (isAnchorLeft) -1f else 1f)
        val renderX = anchorX + slideOffset + dragOffsetX

        lastRenderX = renderX
        lastRenderY = y
        lastWidth = width
        lastHeight = height

        if (s in 0.03f..0.98f) {
            val slideVx = (1f - s) * 20f * (if (isAnchorLeft) -1f else 1f)
            MotionBlurHelper.renderHorizontalMotionBlur(
                renderX, y, width, height, radius,
                slideVx,
                NanoVGColor(1f, 1f, 1f, 0.20f * s),
                maxBlur = 10f
            )
        }

        Draw2D.shadow(renderX, y, width, height, radius, 8f, NanoVGColor(0f, 0f, 0f, (0.45f + hover * 0.10f) * s), 2f)

        Draw2D.roundedRect(renderX, y, width, height, radius, NanoVGColor(0.035f, 0.04f, 0.055f, 0.92f * s))

        val borderAlpha = (0.08f + hover * 0.10f) * s
        NanoVGRenderer.drawRoundedOutline(renderX, y, width, height, radius, NanoVGColor(1f, 1f, 1f, borderAlpha), 0.75f)

        var curX = renderX + 10f
        if (useAvatar) {
            AnimeArt.drawAvatar(
                avatarId,
                curX, centerY - 8.5f, 17f,
                alpha = 0.95f * s, ringAlpha = 0.10f * s, zoom = 1.15f, biasY = -0.15f
            )
            curX += 17f + 7f
        } else if (category != null) {
            CategoryVectorIcons.drawCategoryIcon(
                category,
                curX, centerY - iconSize * 0.5f, iconSize,
                type.accentColor.copy(a = 0.95f * s)
            )
            curX += iconSize + 7f
        } else {
            when (type) {
                ToastType.SUCCESS, ToastType.MODULE_ON -> {
                    CategoryVectorIcons.drawCheckmark(
                        curX, centerY - iconSize * 0.5f, iconSize,
                        type.accentColor.copy(a = 0.95f * s)
                    )
                    curX += iconSize + 7f
                }
                ToastType.ERROR -> {
                    CategoryVectorIcons.drawClearIcon(
                        curX, centerY - iconSize * 0.5f, iconSize,
                        type.accentColor.copy(a = 0.95f * s)
                    )
                    curX += iconSize + 7f
                }
                else -> {
                    Draw2D.circle(curX + 3.5f, centerY, 2.5f, type.accentColor.copy(a = 0.95f * s))
                    curX += 10f
                }
            }
        }

        font.drawString(
            title,
            curX,
            centerY - 5f,
            NanoVGColor.WHITE.copy(a = 0.98f * s),
            10.5f,
            shadow = false
        )
        curX += titleW + 8f

        Draw2D.line(curX, y + 6f, curX, y + height - 6f, NanoVGColor(1f, 1f, 1f, 0.09f * s), 1.0f)
        curX += 8f

        font.drawString(
            tagText,
            curX,
            centerY - 4f,
            NanoVGColor(0.65f, 0.68f, 0.76f, 0.85f * s),
            8.5f,
            shadow = false
        )
        curX += tagW + 4f

        if (message.isNotBlank()) {
            val maxAllowedW = width - (curX - renderX) - (if (repeatCount > 1) 36f else (if (hover > 0.05f) 22f else 10f))
            val trimmedMsg = font.trimWithEllipsis(message, maxAllowedW, 10.0f)
            val msgColor = if (type == ToastType.MODULE_ON) {
                NanoVGColor(0.85f, 0.98f, 0.90f, 0.95f * s)
            } else {
                NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f * s)
            }
            font.drawString(
                trimmedMsg,
                curX,
                centerY - 5f,
                msgColor,
                10.0f,
                shadow = false
            )
            curX += font.getStringWidth(trimmedMsg, 10.0f) + 8f
        }

        if (repeatCount > 1) {
            val badgeText = "×$repeatCount"
            val bw = font.getStringWidth(badgeText, 8.5f) + 7f
            val bh = 12f
            val bx = renderX + width - bw - (if (hover > 0.05f) 20f else 8f)
            val by = centerY - bh * 0.5f
            Draw2D.roundedRect(bx, by, bw, bh, 3.5f, NanoVGColor(1f, 1f, 1f, 0.12f * s))
            Draw2D.roundedOutline(bx, by, bw, bh, 3.5f, NanoVGColor(1f, 1f, 1f, 0.16f * s), 0.75f)
            font.drawCenteredString(
                badgeText, bx + bw * 0.5f, centerY,
                NanoVGColor(1f, 1f, 1f, 0.95f * s), 8.5f, shadow = false
            )
        }

        if (hover > 0.05f) {
            val closeSize = 10f
            val closeX = renderX + width - closeSize - 8f
            val closeY = centerY - closeSize * 0.5f
            CategoryVectorIcons.drawClearIcon(
                closeX, closeY, closeSize,
                NanoVGColor(1f, 1f, 1f, (0.50f + hover * 0.45f) * s)
            )
        }

        val progress = getProgress()
        val barPad = 6f
        val maxBarW = width - barPad * 2f
        val barW = maxBarW * progress
        val barH = 1.0f
        val barY = y + height - 1.6f

        Draw2D.roundedRect(renderX + barPad, barY, maxBarW, barH, 0.5f, NanoVGColor(1f, 1f, 1f, 0.06f * s))

        if (barW > 0.5f) {
            val progressColor = if (isHovered) NanoVGColor(1f, 1f, 1f, 0.65f * s) else type.accentColor.copy(a = 0.55f * s)
            Draw2D.roundedRect(renderX + barPad, barY, barW, barH, 0.5f, progressColor)
        }
    }


    internal fun ToastNotification.renderFluidCompact(
        anchorX: Float,
        y: Float,
        isAnchorLeft: Boolean,
        s: Float,
        pulse: Float,
        hover: Float
    ) {
        val font: FontRenderer = RenderEngine.fontRenderer

        val titleW = font.getStringWidth(title, 10.5f)
        val msgW = if (message.isNotBlank()) font.getStringWidth(message, 9.5f) else 0f
        val totalTextW = titleW + (if (message.isNotBlank()) msgW + 12f else 0f)
        val width = (totalTextW + 48f + (if (repeatCount > 1) 24f else 0f)).coerceIn(140f, 400f) * pulse
        val height = 28f * pulse
        val radius = height * 0.5f

        val slideOffset = (1f - s) * (width + 30f) * (if (isAnchorLeft) -1f else 1f)
        val renderX = anchorX + slideOffset + dragOffsetX

        lastRenderX = renderX
        lastRenderY = y
        lastWidth = width
        lastHeight = height

        Draw2D.shadow(renderX, y, width, height, radius, 10f, NanoVGColor(0f, 0f, 0f, 0.50f * s), 2f)
        Draw2D.roundedRect(renderX, y, width, height, radius, NanoVGColor(0.05f, 0.06f, 0.08f, 0.92f * s))
        Draw2D.roundedOutline(renderX, y, width, height, radius, NanoVGColor(1f, 1f, 1f, (0.08f + hover * 0.12f) * s), 0.8f)

        val dotRadius = 3f
        val dotX = renderX + 9f
        val dotY = y + height * 0.5f
        Draw2D.circle(dotX, dotY, dotRadius, type.accentColor.copy(a = 0.90f * s))

        val textBlur = MotionBlurHelper.getBlurForProgress(s, maxBlur = 4.0f)
        val textX = dotX + 8f
        font.drawString(
            title, textX, dotY,
            NanoVGColor(1f, 1f, 1f, 0.95f * s),
            10.5f, TextAlignment.MIDDLE_LEFT, shadow = false, blur = textBlur
        )

        var curOffset = textX + titleW + 5f
        if (message.isNotBlank()) {
            Draw2D.circle(curOffset + 1f, dotY, 1f, NanoVGColor(1f, 1f, 1f, 0.25f * s))
            curOffset += 6f
            val maxAllowedW = width - (curOffset - renderX) - (if (repeatCount > 1) 36f else 12f)
            val trimmed = font.trimWithEllipsis(message, maxAllowedW, 9.5f)
            font.drawString(
                trimmed, curOffset, dotY,
                NanoVGColor(0.65f, 0.65f, 0.70f, 0.90f * s),
                9.5f, TextAlignment.MIDDLE_LEFT, shadow = false, blur = textBlur
            )
        }

        if (repeatCount > 1) {
            val badgeText = "×$repeatCount"
            val badgeW = font.getStringWidth(badgeText, 8f) + 6f
            val badgeH = 11f
            val badgeX = renderX + width - badgeW - 6f
            val badgeY = y + (height - badgeH) * 0.5f
            Draw2D.roundedRect(badgeX, badgeY, badgeW, badgeH, 3.5f, NanoVGColor(1f, 1f, 1f, 0.12f * s))
            font.drawCenteredString(
                badgeText, badgeX + badgeW * 0.5f, badgeY + badgeH * 0.5f,
                NanoVGColor(1f, 1f, 1f, 0.95f * s), 8f, shadow = false
            )
        }
    }
