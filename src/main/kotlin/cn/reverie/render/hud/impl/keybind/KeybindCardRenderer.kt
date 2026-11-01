package cn.reverie.render.hud.impl.keybind
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.nanovg.NanoVGTransform
import kotlin.math.*
internal fun KeybindHUDWidget.renderCard(
    font: FontRenderer,
    items: List<KeybindDisplayItem>,
    isEditing: Boolean,
    partialTicks: Float
) {
    val textShadow = HUDModule.textShadow.value
    val headerH = 23f
    val baseRowH = 18f
    val padTop = 5f
    val padBottom = 5f
    val radius = cornerRadius.getFloat()
    val baseIconColor = resolveIconColor()
    val pulse = headerPulseAnim.value
    val iconColor = if (pulse > 0.01f) baseIconColor.lerp(NanoVGColor.WHITE, 0.35f * pulse) else baseIconColor

    val nameFs = 9.0f
    val keyFs = 8.5f
    val stateFs = 8.0f

    var maxContentW = 146f
    for (item in items) {
        val nameW = font.getStringWidth(item.name, nameFs)
        val keyStrW = font.getStringWidth(item.keyName, keyFs)
        val keyBadgeW = when (keyStyle.value) {
            "Capsule" -> keyStrW + 9f
            "Brackets" -> font.getStringWidth("[${item.keyName}]", keyFs)
            else -> keyStrW
        }
        val stateW = if (item.stateText != null) font.getStringWidth(item.stateText, stateFs) + 5f else 0f
        val itemTotalW = 28f + nameW + 8f + keyBadgeW + stateW + 9f
        maxContentW = max(maxContentW, itemTotalW)
    }

    widthAnim.animateTo(maxContentW)
    width = widthAnim.value

    val totalActiveRowsH = items.sumOf { (baseRowH * it.collapseFactor.coerceIn(0f, 1f)).toDouble() }.toFloat()
    val isEmpty = items.isEmpty() || totalActiveRowsH <= 0.5f

    emptyFadeAnim.animateTo(if (isEmpty) 1f else 0f)

    val cardH = if (isEmpty) {
        headerH + 22f
    } else {
        headerH + padTop + padBottom + totalActiveRowsH
    }
    height = cardH

    val bgAlpha = HUDModule.bgOpacity.getFloat()
    if (background.value) {
        drawWidgetShadow(x, y, width, height, radius, 0.45f, blur = 8f)
        drawBackdrop(x, y, width, height, radius, cardBg.copy(a = bgAlpha))
        if (outline.value) {
            NanoVGRenderer.drawRoundedOutline(
                x, y, width, height, radius,
                NanoVGColor(1f, 1f, 1f, 0.075f), 0.75f
            )
        }
    }

    val headerCenterY = y + headerH * 0.5f
    var curHeaderX = x + 8.5f

    val iconSize = 12f
    val iconD = when (iconType.value) {
        "Command" -> HudVectorIcons.COMMAND
        "Keycap" -> HudVectorIcons.KEYCAP
        else -> HudVectorIcons.KEYBOARD
    }

    val transform = NanoVGTransform(Draw2D.context)
    if (pulse > 0.01f) {
        val icx = curHeaderX + iconSize * 0.5f
        val icy = headerCenterY
        val iconScale = 1.0f + 0.12f * pulse
        transform.withTransform {
            transform.translate(icx, icy)
            transform.scale(iconScale)
            transform.translate(-icx, -icy)
            HudVectorIcons.draw(
                iconD,
                curHeaderX,
                headerCenterY - iconSize * 0.5f,
                iconSize,
                iconColor,
                strokeWidth = 1.35f + 0.2f * pulse
            )
        }
    } else {
        HudVectorIcons.draw(
            iconD,
            curHeaderX,
            headerCenterY - iconSize * 0.5f,
            iconSize,
            iconColor,
            strokeWidth = 1.35f
        )
    }
    curHeaderX += iconSize + 6.0f

    font.drawString(
        "KEYBINDS",
        curHeaderX,
        headerCenterY - 4.5f,
        NanoVGColor(0.95f, 0.96f, 0.98f, 0.95f),
        9.5f,
        shadow = textShadow
    )

    if (showCount.value) {
        val activeCount = items.count { it.isEnabled && it.collapseFactor > 0.5f }
        val countStr = if (onlyEnabled.value) "$activeCount" else "$activeCount/${items.count { it.collapseFactor > 0.5f }}"
        val countFs = 8.0f
        val countTextW = font.getStringWidth(countStr, countFs)
        val countBadgeW = countTextW + 8f
        val countBadgeH = 13f
        val countBadgeX = x + width - countBadgeW - 7.5f
        val countBadgeY = headerCenterY - countBadgeH * 0.5f

        NanoVGRenderer.drawRoundedRect(
            countBadgeX, countBadgeY, countBadgeW, countBadgeH,
            3.0f, NanoVGColor(1f, 1f, 1f, 0.055f)
        )
        NanoVGRenderer.drawRoundedOutline(
            countBadgeX, countBadgeY, countBadgeW, countBadgeH,
            3.0f, NanoVGColor(1f, 1f, 1f, 0.08f), 0.75f
        )
        font.drawString(
            countStr,
            countBadgeX + 4f,
            headerCenterY - 4.0f,
            NanoVGColor(0.85f, 0.88f, 0.94f, 0.90f),
            countFs,
            shadow = textShadow
        )
    }

    Draw2D.line(
        x + 7f, y + headerH,
        x + width - 7f, y + headerH,
        NanoVGColor(1f, 1f, 1f, 0.06f), 0.75f
    )

    var curY = y + headerH + padTop

    if (emptyFadeAnim.value > 0.01f && isEmpty) {
        val emptyMsg = "No active keybinds"
        val emptyFs = 8.5f
        val emptyW = font.getStringWidth(emptyMsg, emptyFs)
        val emptyAlpha = emptyFadeAnim.value
        font.drawString(
            emptyMsg,
            x + (width - emptyW) * 0.5f,
            curY + 3f,
            NanoVGColor(0.55f, 0.58f, 0.65f, 0.70f * emptyAlpha),
            emptyFs,
            shadow = textShadow
        )
    } else {
        for (item in items) {
            val rowH = renderAnimatedCardRow(font, item, curY, baseRowH, nameFs, keyFs, textShadow, iconColor)
            curY += rowH
        }
    }

    if (isEditing) {
        renderEditingBounds(partialTicks)
    }
}

internal fun KeybindHUDWidget.renderAnimatedCardRow(
    font: FontRenderer,
    item: KeybindDisplayItem,
    rowY: Float,
    baseRowH: Float,
    nameFs: Float,
    keyFs: Float,
    textShadow: Boolean,
    accent: NanoVGColor
): Float {
    val collapse = item.collapseFactor.coerceIn(0f, 1f)
    val alpha = item.alphaFactor.coerceIn(0f, 1f)
    val slide = item.slideFactor.coerceIn(0f, 1f)
    val swProg = item.switchProgress.coerceIn(0f, 1f)

    val effectiveRowH = baseRowH * collapse
    if (effectiveRowH <= 0.3f || alpha <= 0.005f) {
        return effectiveRowH
    }

    val padX = 8.5f
    val centerY = rowY + effectiveRowH * 0.5f
    val rowAlpha = (alpha * (if (collapse < 0.6f) collapse / 0.6f else 1f)).coerceIn(0f, 1f)
    val slideOffset = (1f - slide) * 10f

    RenderEngine.nanoVG.scissor.withScissor(x, rowY, width, effectiveRowH) {
        if (item.flashAlpha > 0.02f) {
            val flashW = width - padX * 2f
            val flashH = baseRowH - 2f
            val flashY = centerY - flashH * 0.5f
            val flashColor = accent.withAlpha(0.20f * item.flashAlpha * rowAlpha)
            NanoVGRenderer.drawRoundedRect(x + padX, flashY, flashW, flashH, 3.0f, flashColor)
        }

        // Draw fluid miniature toggle switch
        val swW = 12f
        val swH = 7.5f
        val swX = x + padX + slideOffset
        val swY = centerY - swH * 0.5f
        drawLiquidToggleSwitch(swX, swY, swW, swH, swProg, rowAlpha, item.flashAlpha)

        val nameColor = if (item.isEnabled) {
            NanoVGColor(0.95f, 0.96f, 0.99f, 0.96f * rowAlpha)
        } else {
            NanoVGColor(0.58f, 0.61f, 0.68f, 0.70f * rowAlpha)
        }

        val rightX = x + width - padX

        if (showKey.value) {
            renderKeyBadge(font, item.keyName, rightX, centerY, keyFs, rowAlpha, textShadow)
        }

        val leftNameX = swX + swW + 5.5f
        val keyW = if (showKey.value) font.getStringWidth(item.keyName, keyFs) + 12f else 0f
        val maxNameW = (rightX - keyW - leftNameX).coerceAtLeast(10f)
        val trimmedName = font.trimWithEllipsis(item.name, maxNameW, nameFs)
        font.drawString(
            trimmedName,
            leftNameX,
            centerY - nameFs * 0.5f,
            nameColor,
            nameFs,
            shadow = textShadow
        )
    }

    return effectiveRowH
}
