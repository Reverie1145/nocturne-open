package cn.reverie.render.hud.impl.arraylist
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
internal fun ArrayListWidget.renderLegacy(partialTicks: Float, isEditing: Boolean) {
    val font: FontRenderer = RenderEngine.fontRenderer
    val fs = fontSize.getFloat()
    val lineH = fs * spacing.getFloat()
    val padX = padding.getFloat()
    val rightSide = alignsRight()
    val textShadow = HUDModule.textShadow.value
    val fadeMode = HUDModule.colorMode.isMode("Fade")

    val entries = collectEntries(fs, isEditing)
    if (entries.isEmpty()) {
        width = 90f
        height = 20f
        if (isEditing) renderEditingBounds(partialTicks)
        return
    }

    sortEntries(entries)

    val hasLeftBar = bar.isMode("Left") || bar.isMode("Both")
    val hasRightBar = bar.isMode("Right") || bar.isMode("Both")
    val barW = 2.5f
    val leftBarPad = if (hasLeftBar) barW + 2f else 0f
    val rightBarPad = if (hasRightBar) barW + 2f else 0f
    val extraBarPad = leftBarPad + rightBarPad

    val screenW = ResolutionHelper.scaledWidth
    width = (entries.maxOfOrNull { it.textWidth } ?: 80f) + padX * 2f + extraBarPad

    if (rightSide) {
        if (x + width > screenW) {
            x = (screenW - width).coerceAtLeast(0f)
        }
    } else {
        if (x < 0f) x = 0f
    }

    class LegacyRow(
        val entry: DisplayEntry,
        val index: Int,
        val left: Float,
        val width: Float,
        val top: Float,
        val height: Float,
        val color: NanoVGColor,
        val suffixColor: NanoVGColor,
        val slide: Float
    )

    val activeRows = mutableListOf<LegacyRow>()
    var curY = y

    for (index in entries.indices) {
        val entry = entries[index]
        val slide = entry.slideFactor.coerceIn(0f, 1f)
        val collapse = entry.collapseFactor.coerceIn(0f, 1f)
        val rowH = lineH * collapse

        if (rowH <= 0.5f || slide <= 0.005f) continue

        val mainColor = resolveColor(index, entries.size, slide)
        val suffixColor = legacySuffixColor.multiplyAlpha(slide)

        val rowW = entry.textWidth + padX * 2f + extraBarPad
        val slideOffset = (1f - slide) * (rowW + 30f) * (if (rightSide) 1f else -1f)
        val rowLeft = if (rightSide) x + width - rowW + slideOffset else x + slideOffset

        activeRows.add(
            LegacyRow(entry, index, rowLeft, rowW, curY, rowH, mainColor, suffixColor, slide)
        )

        curY += rowH
    }

    if (activeRows.isEmpty()) {
        height = 20f
        if (isEditing) renderEditingBounds(partialTicks)
        return
    }

    height = (curY - y).coerceAtLeast(lineH)

    for (row in activeRows) {
        if (row.slide in 0.02f..0.99f) {
            val slideOffset = (1f - row.slide) * (row.width + 30f) * (if (rightSide) 1f else -1f)
            cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                row.left, row.top, row.width, row.height,
                0f,
                slideOffset * 0.6f,
                row.color.multiplyAlpha(0.24f * row.slide),
                maxBlur = 14f
            )
        }

        if (background.value) {
            val alpha = (HUDModule.bgOpacity.getFloat() * row.slide).coerceIn(0f, 1f)
            drawWidgetShadow(row.left, row.top, row.width, row.height, 0f, 0.40f * row.slide, blur = 6f, colorOverride = row.color)
            drawBackdrop(
                row.left, row.top, row.width, row.height, 0f, NanoVGColor(0f, 0f, 0f, alpha),
                sampleRegion = Rect2f(x, y, width, height)
            )
            if (outline.value) {
                NanoVGRenderer.drawRoundedOutline(row.left, row.top, row.width, row.height, 0f, NanoVGColor(1f, 1f, 1f, 0.06f * row.slide), 0.75f)
            }
        }
    }

    if (hasLeftBar || hasRightBar) {
        val totalRows = activeRows.size
        for (i in 0 until totalRows) {
            val curr = activeRows[i]
            val isLast = i == totalRows - 1
            val next = if (i < totalRows - 1) activeRows[i + 1] else null
            val seamOverlap = if (!isLast) 0.35f else 0f

            if (hasLeftBar) {
                val barX = curr.left
                val barY = curr.top
                val barH = curr.height + seamOverlap
                Draw2D.rect(barX, barY, barW, barH, curr.color)

                if (rightSide && next != null && kotlin.math.abs(next.left - curr.left) > 0.5f) {
                    val stepX = minOf(curr.left, next.left)
                    val stepW = kotlin.math.abs(next.left - curr.left) + barW
                    val stepY = curr.top + curr.height - barW
                    val stepColor = curr.color.lerp(next.color, 0.5f)
                    Draw2D.rect(stepX, stepY, stepW, barW + 0.35f, stepColor)
                }
            }

            if (hasRightBar) {
                val barX = curr.left + curr.width - barW
                val barY = curr.top
                val barH = curr.height + seamOverlap
                Draw2D.rect(barX, barY, barW, barH, curr.color)

                if (!rightSide && next != null) {
                    val currRight = curr.left + curr.width
                    val nextRight = next.left + next.width
                    if (kotlin.math.abs(nextRight - currRight) > 0.5f) {
                        val stepX = minOf(currRight - barW, nextRight - barW)
                        val stepW = kotlin.math.abs(nextRight - currRight) + barW
                        val stepY = curr.top + curr.height - barW
                        val stepColor = curr.color.lerp(next.color, 0.5f)
                        Draw2D.rect(stepX, stepY, stepW, barW + 0.35f, stepColor)
                    }
                }
            }
        }
    }

    for (row in activeRows) {
        val textBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(row.slide, maxBlur = 5.5f)
        val textTop = row.top + (row.height - fs) * 0.5f

        if (row.width > 0.5f && row.height > 0.5f) {
            NanoVGRenderer.withScissor(row.left, row.top, row.width, row.height) {
                if (rightSide) {
                    val textRight = row.left + row.width - padX - rightBarPad
                    if (row.entry.suffix != null) {
                        font.drawString(row.entry.suffix, textRight, textTop, row.suffixColor, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        if (fadeMode) {
                            font.drawHorizontalFadeString(row.entry.mainName, textRight - row.entry.suffixWidth, textTop, fs, row.slide, TextAlignment.TOP_RIGHT, textShadow, textBlur)
                        } else {
                            font.drawString(row.entry.mainName, textRight - row.entry.suffixWidth, textTop, row.color, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        }
                    } else {
                        if (fadeMode) {
                            font.drawHorizontalFadeString(row.entry.totalText, textRight, textTop, fs, row.slide, TextAlignment.TOP_RIGHT, textShadow, textBlur)
                        } else {
                            font.drawString(row.entry.totalText, textRight, textTop, row.color, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        }
                    }
                } else {
                    var textX = row.left + padX + leftBarPad
                    if (row.entry.suffix != null) {
                        textX += if (fadeMode) {
                            font.drawHorizontalFadeString(row.entry.mainName, textX, textTop, fs, row.slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                        } else {
                            font.drawString(row.entry.mainName, textX, textTop, row.color, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
                        }
                        font.drawString(row.entry.suffix, textX, textTop, row.suffixColor, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
                    } else {
                        if (fadeMode) {
                            font.drawHorizontalFadeString(row.entry.totalText, textX, textTop, fs, row.slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                        } else {
                            font.drawString(row.entry.totalText, textX, textTop, row.color, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
                        }
                    }
                }
            }
        }
    }

    if (isEditing) {
        renderEditingBounds(partialTicks)
    }
}

internal fun ArrayListWidget.renderZinc(partialTicks: Float, isEditing: Boolean) {
    val font: FontRenderer = RenderEngine.fontRenderer
    val fs = 11.5f
    val baseLineH = 15.5f
    val rightSide = alignsRight()
    val textShadow = HUDModule.textShadow.value
    val fadeMode = HUDModule.colorMode.isMode("Fade")

    val entries = collectEntries(fs, isEditing)
    if (entries.isEmpty()) {
        width = 90f
        height = 20f
        if (isEditing) renderEditingBounds(partialTicks)
        return
    }

    sortEntries(entries)

    val hasLeftBar = bar.isMode("Left") || bar.isMode("Both")
    val hasRightBar = bar.isMode("Right") || bar.isMode("Both")
    val barW = 2.5f
    val leftBarPad = if (hasLeftBar) barW + 2f else 0f
    val rightBarPad = if (hasRightBar) barW + 2f else 0f
    val extraBarPad = leftBarPad + rightBarPad

    val maxTextW = entries.maxOfOrNull { it.textWidth } ?: 80f
    val itemPadH = 7f
    width = maxTextW + itemPadH * 2f + extraBarPad

    val screenW = ResolutionHelper.scaledWidth
    if (rightSide) {
        if (x + width > screenW) {
            x = (screenW - width).coerceAtLeast(0f)
        }
    } else {
        if (x < 0f) x = 0f
    }

    var curY = y
    val totalCount = entries.size
    height = entries.sumOf {
        val slide = it.slideFactor.coerceIn(0f, 1f)
        val rowH = baseLineH * it.collapseFactor.coerceIn(0f, 1f)
        if (rowH > 0.5f && slide > 0.005f) rowH.toDouble() else 0.0
    }.toFloat().coerceAtLeast(baseLineH)

    for (index in entries.indices) {
        val entry = entries[index]
        val slide = entry.slideFactor.coerceIn(0f, 1f)
        val collapse = entry.collapseFactor.coerceIn(0f, 1f)
        val lineH = baseLineH * collapse

        if (lineH <= 0.5f || slide <= 0.005f) continue

        val mainColor = resolveColor(index, totalCount, slide)

        val suffixColor = zincSuffixColor.multiplyAlpha(slide)

        val slideOffset = (1f - slide) * (width + 24f) * (if (rightSide) 1f else -1f)
        val itemW = entry.textWidth + itemPadH * 2f + extraBarPad

        val itemX = if (rightSide) {
            x + width - itemW + slideOffset
        } else {
            x + slideOffset
        }

        if (slide in 0.02f..0.99f) {
            cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                itemX, curY, itemW, lineH,
                3.0f,
                slideOffset * 0.5f,
                mainColor.multiplyAlpha(0.24f * slide),
                maxBlur = 14f
            )
        }

        val textBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(slide, maxBlur = 5.5f)

        if (background.value) {
            val bgAlphaValue = (HUDModule.bgOpacity.getFloat() * slide).coerceIn(0f, 1f)
            drawWidgetShadow(itemX, curY, itemW, lineH, 3.0f, 0.38f * slide, blur = 5f, colorOverride = mainColor)
            drawBackdrop(
                itemX, curY, itemW, lineH, 3.0f, NanoVGColor(0f, 0f, 0f, bgAlphaValue),
                sampleRegion = Rect2f(x, y, width, height)
            )

            if (outline.value) {
                NanoVGRenderer.drawRoundedOutline(itemX, curY, itemW, lineH, 3.0f, NanoVGColor(1f, 1f, 1f, 0.065f * slide), 0.75f)
            }
        }

        if (itemW > 0.5f && lineH > 0.5f) {
            if (hasLeftBar) {
                Draw2D.roundedRect(itemX, curY, barW, lineH, 1.5f, mainColor)
            }
            if (hasRightBar) {
                Draw2D.roundedRect(itemX + itemW - barW, curY, barW, lineH, 1.5f, mainColor)
            }

            NanoVGRenderer.withScissor(itemX, curY, itemW, lineH) {
                val textX = itemX + itemPadH + leftBarPad
                val textY = curY + (lineH - fs) * 0.5f

                if (entry.suffix != null) {
                    val nameW = if (fadeMode) {
                        font.drawHorizontalFadeString(entry.mainName, textX, textY, fs, slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                    } else {
                        font.drawString(entry.mainName, textX, textY, mainColor, fs, shadow = textShadow, blur = textBlur)
                    }
                    font.drawString(entry.suffix, textX + nameW, textY, suffixColor, fs, shadow = textShadow, blur = textBlur)
                } else {
                    if (fadeMode) {
                        font.drawHorizontalFadeString(entry.totalText, textX, textY, fs, slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                    } else {
                        font.drawString(entry.totalText, textX, textY, mainColor, fs, shadow = textShadow, blur = textBlur)
                    }
                }
            }
        }

        curY += lineH + 1.5f
    }

    height = (curY - y).coerceAtLeast(baseLineH)

    if (isEditing) {
        renderEditingBounds(partialTicks)
    }
}
