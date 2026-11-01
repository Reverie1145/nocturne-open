package cn.reverie.render.hud.impl.keybind
import cn.reverie.module.Module
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.nanovg.NanoVGTransform
import kotlin.math.*
internal fun KeybindHUDWidget.renderPillStyle(
    font: FontRenderer,
    items: List<KeybindDisplayItem>,
    isEditing: Boolean,
    partialTicks: Float
) {
    val radius = cornerRadius.getFloat()
    val spacing = pillSpacing.getFloat()
    val textShadow = HUDModule.textShadow.value
    val bgAlpha = HUDModule.bgOpacity.getFloat().coerceIn(0.20f, 1f)
    val outlineColor = NanoVGColor(1f, 1f, 1f, 0.08f)

    // 1. Header Pill (Keybinds)
    val headerH = 19f
    val headerFs = 9.5f
    val headerIconSize = 12f
    val headerText = "Keybinds"
    val headerTextW = font.getStringWidth(headerText, headerFs)
    val headerW = 7.5f + headerIconSize + 5.5f + headerTextW + 9f

    val baseIconColor = resolveIconColor()
    val pulse = headerPulseAnim.value
    val iconColor = if (pulse > 0.01f) baseIconColor.lerp(NanoVGColor.WHITE, 0.35f * pulse) else baseIconColor
    val iconD = when (iconType.value) {
        "Command" -> HudVectorIcons.COMMAND
        "Keycap" -> HudVectorIcons.KEYCAP
        else -> HudVectorIcons.KEYBOARD
    }

    if (background.value) {
        drawWidgetShadow(x, y, headerW, headerH, radius, 0.45f, blur = 6f)
        drawBackdrop(x, y, headerW, headerH, radius, cardBg.copy(a = bgAlpha))
        if (outline.value) {
            NanoVGRenderer.drawRoundedOutline(x, y, headerW, headerH, radius, outlineColor, 0.75f)
        }
    }

    val headerCenterY = y + headerH * 0.5f
    val transform = NanoVGTransform(Draw2D.context)

    if (pulse > 0.01f) {
        val icx = x + 7.5f + headerIconSize * 0.5f
        val icy = headerCenterY
        val iconScale = 1.0f + 0.12f * pulse
        transform.withTransform {
            transform.translate(icx, icy)
            transform.scale(iconScale)
            transform.translate(-icx, -icy)
            HudVectorIcons.draw(
                iconD,
                x + 7.5f,
                headerCenterY - headerIconSize * 0.5f,
                headerIconSize,
                iconColor,
                strokeWidth = 1.35f + 0.2f * pulse
            )
        }
    } else {
        HudVectorIcons.draw(
            iconD,
            x + 7.5f,
            headerCenterY - headerIconSize * 0.5f,
            headerIconSize,
            iconColor,
            strokeWidth = 1.35f
        )
    }

    font.drawString(
        headerText,
        x + 7.5f + headerIconSize + 5.5f,
        headerCenterY - headerFs * 0.5f + 0.5f,
        NanoVGColor(0.96f, 0.97f, 1.0f, 0.98f),
        headerFs,
        shadow = textShadow
    )

    // 2. Items measurement & rendering
    val rowH = 18f
    val nameFs = 9.5f
    val keyFs = 8.0f
    val layout = switchLayout.value

    var maxRowW = headerW
    for (item in items) {
        val nameW = font.getStringWidth(item.name, nameFs)
        val keyW = if (showKey.value) {
            when (keyStyle.value) {
                "Capsule" -> font.getStringWidth(item.keyName, keyFs) + 8f
                "Brackets" -> font.getStringWidth("[${item.keyName}]", keyFs) + 4f
                else -> font.getStringWidth(item.keyName, keyFs) + 4f
            }
        } else 0f

        val totalItemW = when (layout) {
            "Separate" -> 19f + 3.5f + (8f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f)
            "Connected" -> 7f + 14f + 5.5f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f
            else -> 8f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f
        }
        maxRowW = max(maxRowW, totalItemW)
    }

    widthAnim.animateTo(maxRowW)
    width = widthAnim.value

    var curY = y + headerH + spacing
    var totalRowsH = 0f

    val activeItems = items.filter { it.collapseFactor > 0.002f }

    if (activeItems.isEmpty()) {
        val emptyMsg = "No active keybinds"
        val emptyFs = 8.5f
        val emptyW = font.getStringWidth(emptyMsg, emptyFs)
        val emptyPillW = emptyW + 16f
        val emptyH = 17f

        if (background.value) {
            drawWidgetShadow(x, curY, emptyPillW, emptyH, radius, 0.35f, blur = 5f)
            drawBackdrop(x, curY, emptyPillW, emptyH, radius, cardBg.copy(a = bgAlpha * 0.85f))
            if (outline.value) {
                NanoVGRenderer.drawRoundedOutline(x, curY, emptyPillW, emptyH, radius, outlineColor, 0.75f)
            }
        }
        font.drawString(
            emptyMsg,
            x + 8f,
            curY + emptyH * 0.5f - emptyFs * 0.5f,
            NanoVGColor(0.55f, 0.58f, 0.65f, 0.75f),
            emptyFs,
            shadow = textShadow
        )
        height = headerH + spacing + emptyH
    } else {
        for (item in activeItems) {
            val collapse = item.collapseFactor.coerceIn(0f, 1f)
            val alpha = item.alphaFactor.coerceIn(0f, 1f)
            val slide = item.slideFactor.coerceIn(0f, 1f)
            val swProg = item.switchProgress.coerceIn(0f, 1f)
            val effectiveH = (rowH + spacing) * collapse

            if (effectiveH <= 0.5f || alpha <= 0.005f) continue

            // Smooth vertical tracking
            val targetY = curY
            val state = item.module?.let { animMap[it] }
            val curRenderY = if (state != null) {
                if (state.currentY < 0f) {
                    state.currentY = targetY
                } else {
                    state.currentY += (targetY - state.currentY) * 0.32f
                    if (abs(state.currentY - targetY) < 0.04f) {
                        state.currentY = targetY
                    }
                }
                state.currentY
            } else targetY

            val rowAlpha = (alpha * (if (collapse < 0.6f) collapse / 0.6f else 1f)).coerceIn(0f, 1f)
            val slideOffset = (1f - slide) * -14f

            // Elastic scale pop
            val rawProg = item.scaleFactor.coerceIn(0f, 1f)
            val scaleVal = when (animStyle.value) {
                "Fluid Spring" -> {
                    if (rawProg >= 0.999f) 1.0f else (0.70f + 0.30f * CubicBezier.EASE_OUT_BACK(rawProg)).coerceIn(0.6f, 1.08f)
                }
                "Smooth Bezier" -> {
                    0.80f + 0.20f * rawProg
                }
                else -> 1.0f
            }

            RenderEngine.nanoVG.scissor.withScissor(x - 30f, curRenderY - 4f, width + 60f, effectiveH + 8f) {
                val centerY = curRenderY + rowH * 0.5f
                val nameW = font.getStringWidth(item.name, nameFs)
                val keyStr = item.keyName
                val keyW = if (showKey.value) {
                    when (keyStyle.value) {
                        "Capsule" -> font.getStringWidth(keyStr, keyFs) + 7f
                        "Brackets" -> font.getStringWidth("[$keyStr]", keyFs) + 3f
                        else -> font.getStringWidth(keyStr, keyFs) + 3f
                    }
                } else 0f

                val nameColor = if (item.isEnabled) {
                    NanoVGColor(0.96f, 0.97f, 1.0f, 0.98f * rowAlpha)
                } else {
                    NanoVGColor(0.60f, 0.63f, 0.70f, 0.75f * rowAlpha)
                }

                when (layout) {
                    "Separate" -> {
                        val swPillW = 19f
                        val swPillX = x + slideOffset
                        val namePillX = swPillX + swPillW + 3.5f
                        val namePillW = 8f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f

                        val totalRowCenter = swPillX + (swPillW + 3.5f + namePillW) * 0.5f

                        transform.withTransform {
                            if (scaleVal != 1.0f) {
                                transform.translate(totalRowCenter, centerY)
                                transform.scale(scaleVal)
                                transform.translate(-totalRowCenter, -centerY)
                            }

                            // 1. Left Switch Pill
                            if (background.value) {
                                drawWidgetShadow(swPillX, curRenderY, swPillW, rowH, radius, 0.40f, blur = 5f)
                                drawBackdrop(swPillX, curRenderY, swPillW, rowH, radius, cardBg.copy(a = bgAlpha * rowAlpha))
                                if (outline.value) {
                                    NanoVGRenderer.drawRoundedOutline(swPillX, curRenderY, swPillW, rowH, radius, outlineColor.withAlpha(0.08f * rowAlpha), 0.75f)
                                }
                            }

                            val swW = 12.5f
                            val swH = 8.0f
                            val swX = swPillX + (swPillW - swW) * 0.5f
                            val swY = centerY - swH * 0.5f
                            drawLiquidToggleSwitch(swX, swY, swW, swH, swProg, rowAlpha, item.flashAlpha)

                            // 2. Right Module Name Pill
                            if (background.value) {
                                drawWidgetShadow(namePillX, curRenderY, namePillW, rowH, radius, 0.40f, blur = 5f)
                                drawBackdrop(namePillX, curRenderY, namePillW, rowH, radius, cardBg.copy(a = bgAlpha * rowAlpha))
                                if (outline.value) {
                                    NanoVGRenderer.drawRoundedOutline(namePillX, curRenderY, namePillW, rowH, radius, outlineColor.withAlpha(0.08f * rowAlpha), 0.75f)
                                }
                            }

                            if (item.flashAlpha > 0.02f) {
                                val flashCol = iconColor.withAlpha(0.20f * item.flashAlpha * rowAlpha)
                                NanoVGRenderer.drawRoundedRect(namePillX, curRenderY, namePillW, rowH, radius, flashCol)
                            }

                            font.drawString(
                                item.name,
                                namePillX + 8f,
                                centerY - nameFs * 0.5f + 0.5f,
                                nameColor,
                                nameFs,
                                shadow = textShadow
                            )

                            if (showKey.value) {
                                renderKeyBadge(font, keyStr, namePillX + namePillW - 7f, centerY, keyFs, rowAlpha, textShadow)
                            }
                        }
                    }

                    "Connected" -> {
                        val pillX = x + slideOffset
                        val swW = 12.5f
                        val swH = 8.0f
                        val pillW = 7f + swW + 5.5f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f
                        val pillCenter = pillX + pillW * 0.5f

                        transform.withTransform {
                            if (scaleVal != 1.0f) {
                                transform.translate(pillCenter, centerY)
                                transform.scale(scaleVal)
                                transform.translate(-pillCenter, -centerY)
                            }

                            if (background.value) {
                                drawWidgetShadow(pillX, curRenderY, pillW, rowH, radius, 0.40f, blur = 5f)
                                drawBackdrop(pillX, curRenderY, pillW, rowH, radius, cardBg.copy(a = bgAlpha * rowAlpha))
                                if (outline.value) {
                                    NanoVGRenderer.drawRoundedOutline(pillX, curRenderY, pillW, rowH, radius, outlineColor.withAlpha(0.08f * rowAlpha), 0.75f)
                                }
                            }

                            if (item.flashAlpha > 0.02f) {
                                val flashCol = iconColor.withAlpha(0.20f * item.flashAlpha * rowAlpha)
                                NanoVGRenderer.drawRoundedRect(pillX, curRenderY, pillW, rowH, radius, flashCol)
                            }

                            val swX = pillX + 7f
                            val swY = centerY - swH * 0.5f
                            drawLiquidToggleSwitch(swX, swY, swW, swH, swProg, rowAlpha, item.flashAlpha)

                            font.drawString(
                                item.name,
                                swX + swW + 5.5f,
                                centerY - nameFs * 0.5f + 0.5f,
                                nameColor,
                                nameFs,
                                shadow = textShadow
                            )

                            if (showKey.value) {
                                renderKeyBadge(font, keyStr, pillX + pillW - 7f, centerY, keyFs, rowAlpha, textShadow)
                            }
                        }
                    }

                    else -> { // None
                        val pillX = x + slideOffset
                        val pillW = 8f + nameW + (if (keyW > 0f) keyW + 4f else 0f) + 8f
                        val pillCenter = pillX + pillW * 0.5f

                        transform.withTransform {
                            if (scaleVal != 1.0f) {
                                transform.translate(pillCenter, centerY)
                                transform.scale(scaleVal)
                                transform.translate(-pillCenter, -centerY)
                            }

                            if (background.value) {
                                drawWidgetShadow(pillX, curRenderY, pillW, rowH, radius, 0.40f, blur = 5f)
                                drawBackdrop(pillX, curRenderY, pillW, rowH, radius, cardBg.copy(a = bgAlpha * rowAlpha))
                                if (outline.value) {
                                    NanoVGRenderer.drawRoundedOutline(pillX, curRenderY, pillW, rowH, radius, outlineColor.withAlpha(0.08f * rowAlpha), 0.75f)
                                }
                            }

                            font.drawString(
                                item.name,
                                pillX + 8f,
                                centerY - nameFs * 0.5f + 0.5f,
                                nameColor,
                                nameFs,
                                shadow = textShadow
                            )

                            if (showKey.value) {
                                renderKeyBadge(font, keyStr, pillX + pillW - 7f, centerY, keyFs, rowAlpha, textShadow)
                            }
                        }
                    }
                }
            }

            curY += effectiveH
            totalRowsH += effectiveH
        }

        height = headerH + spacing + totalRowsH
    }

    if (isEditing) {
        renderEditingBounds(partialTicks)
    }
}

internal fun KeybindHUDWidget.drawLiquidToggleSwitch(
    sx: Float, sy: Float, sw: Float, sh: Float,
    toggleProgress: Float,
    alpha: Float,
    flashProgress: Float = 0f
) {
    val r = sh * 0.5f

    // Colors
    val offBg = NanoVGColor(0.20f, 0.22f, 0.27f, 0.90f * alpha)
    val onBg = NanoVGColor(1.0f, 1.0f, 1.0f, 0.98f * alpha)
    val currentBg = offBg.lerp(onBg, toggleProgress)

    NanoVGRenderer.drawRoundedRect(sx, sy, sw, sh, r, currentBg)

    // Fluid knob with velocity horizontal stretch
    val stretch = sin(toggleProgress * Math.PI.toFloat()).coerceIn(0f, 1f)
    val knobPadding = 1.25f
    val baseDiam = sh - knobPadding * 2f

    // When moving, the knob stretches horizontally like a jelly droplet!
    val knobW = baseDiam + baseDiam * 0.36f * stretch
    val knobH = baseDiam - baseDiam * 0.08f * stretch

    val minKnobLeft = sx + knobPadding
    val maxKnobLeft = sx + sw - knobPadding - knobW
    val knobLeft = minKnobLeft + (maxKnobLeft - minKnobLeft) * toggleProgress
    val knobTop = sy + (sh - knobH) * 0.5f

    val offKnobCol = NanoVGColor(0.70f, 0.72f, 0.78f, 0.95f * alpha)
    val onKnobCol = NanoVGColor(0.09f, 0.09f, 0.11f, 0.98f * alpha)
    val currentKnobCol = offKnobCol.lerp(onKnobCol, toggleProgress)

    NanoVGRenderer.drawRoundedRect(knobLeft, knobTop, knobW, knobH, knobH * 0.5f, currentKnobCol)

    // Ripple pulse ring when triggered
    if (flashProgress > 0.02f) {
        val rippleRadius = r + (1f - flashProgress) * 4.5f
        val rippleAlpha = flashProgress * 0.40f * alpha
        Draw2D.circleOutline(sx + sw * 0.5f, sy + sh * 0.5f, rippleRadius, NanoVGColor(1f, 1f, 1f, rippleAlpha), 0.85f)
    }
}

internal fun KeybindHUDWidget.renderKeyBadge(
    font: FontRenderer,
    keyStr: String,
    rightX: Float,
    centerY: Float,
    keyFs: Float,
    alpha: Float,
    textShadow: Boolean
) {
    when (keyStyle.value) {
        "Capsule" -> {
            val keyStrW = font.getStringWidth(keyStr, keyFs)
            val badgeW = keyStrW + 6.5f
            val badgeH = 11.5f
            val badgeX = rightX - badgeW
            val badgeY = centerY - badgeH * 0.5f

            NanoVGRenderer.drawRoundedRect(
                badgeX, badgeY, badgeW, badgeH, 2.5f,
                NanoVGColor(1f, 1f, 1f, 0.08f * alpha)
            )
            NanoVGRenderer.drawRoundedOutline(
                badgeX, badgeY, badgeW, badgeH, 2.5f,
                NanoVGColor(1f, 1f, 1f, 0.15f * alpha), 0.75f
            )
            font.drawString(
                keyStr,
                badgeX + 3.2f,
                centerY - keyFs * 0.5f + 0.5f,
                NanoVGColor(0.96f, 0.97f, 1f, 0.96f * alpha),
                keyFs,
                shadow = textShadow
            )
        }
        "Brackets" -> {
            val fullStr = "[$keyStr]"
            font.drawString(
                fullStr,
                rightX,
                centerY - keyFs * 0.5f + 0.5f,
                NanoVGColor(0.82f, 0.85f, 0.92f, 0.90f * alpha),
                keyFs,
                TextAlignment.TOP_RIGHT,
                shadow = textShadow
            )
        }
        else -> {
            font.drawString(
                keyStr,
                rightX,
                centerY - keyFs * 0.5f + 0.5f,
                NanoVGColor(0.82f, 0.85f, 0.92f, 0.90f * alpha),
                keyFs,
                TextAlignment.TOP_RIGHT,
                shadow = textShadow
            )
        }
    }
}
