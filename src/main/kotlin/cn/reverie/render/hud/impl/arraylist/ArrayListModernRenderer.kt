package cn.reverie.render.hud.impl.arraylist
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.CornerRadii
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.math.Rect2f
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import cn.reverie.render.nanovg.NanoVGRenderer
internal fun ArrayListWidget.renderModern(partialTicks: Float, isEditing: Boolean) {
    val font: FontRenderer = RenderEngine.fontRenderer
    val fs = fontSize.getFloat()
    val lineH = fs * spacing.getFloat()
    val padX = padding.getFloat()
    val rightSide = alignsRight()
    val textShadow = HUDModule.textShadow.value
    val fadeMode = HUDModule.colorMode.isMode("Fade")
    val r = cornerRadius.getFloat()
    val screenW = ResolutionHelper.scaledWidth

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
    width = maxTextW + padX * 2f + extraBarPad

    if (rightSide) {
        if (x + width > screenW) {
            x = (screenW - width).coerceAtLeast(0f)
        }
    } else {
        if (x < 0f) {
            x = 0f
        }
    }

    val fixedRight = x + width
    val fixedLeft = x

    class ModernRow(
        val entry: DisplayEntry,
        val index: Int,
        val left: Float,
        val right: Float,
        val top: Float,
        val bottom: Float,
        val slide: Float
    )

    val activeRows = mutableListOf<ModernRow>()
    var curY = y

    for (index in entries.indices) {
        val entry = entries[index]
        val slide = entry.slideFactor.coerceIn(0f, 1f)
        val collapse = entry.collapseFactor.coerceIn(0f, 1f)
        val rowH = lineH * collapse

        if (rowH <= 0.5f || slide <= 0.005f) continue

        val rowW = entry.textWidth + padX * 2f + extraBarPad

        val rowLeft: Float
        val rowRight: Float
        if (rightSide) {
            val slideDist = (1f - slide) * (rowW + 15f)
            rowRight = fixedRight
            rowLeft = (fixedRight - rowW + slideDist).coerceAtLeast(fixedRight - rowW)
        } else {
            val slideDist = (1f - slide) * (rowW + 15f)
            rowLeft = fixedLeft
            rowRight = (fixedLeft + rowW - slideDist).coerceAtMost(fixedLeft + rowW)
        }

        activeRows.add(
            ModernRow(
                entry = entry,
                index = index,
                left = rowLeft,
                right = rowRight,
                top = curY,
                bottom = curY + rowH,
                slide = slide
            )
        )

        curY += rowH
    }

    if (activeRows.isEmpty()) {
        height = 20f
        if (isEditing) renderEditingBounds(partialTicks)
        return
    }

    height = (curY - y).coerceAtLeast(lineH)

    if (background.value) {
        val path = NanoVGPath(NanoVGRenderer.context)

        fun buildFilletedPath(p: NanoVGPath) {
            p.begin()
            if (rightSide) {
                val firstRow = activeRows.first()
                p.moveTo(fixedRight, firstRow.top)

                val topEffR = minOf(r, (fixedRight - firstRow.left) * 0.5f, (firstRow.bottom - firstRow.top) * 0.5f)
                val topK = topEffR * 0.55228475f
                p.lineTo(firstRow.left + topEffR, firstRow.top)

                p.bezierTo(
                    firstRow.left + topEffR - topK, firstRow.top,
                    firstRow.left, firstRow.top + topEffR - topK,
                    firstRow.left, firstRow.top + topEffR
                )

                for (i in activeRows.indices) {
                    val curr = activeRows[i]
                    if (i < activeRows.size - 1) {
                        val next = activeRows[i + 1]
                        val diff = kotlin.math.abs(next.left - curr.left)
                        val maxRByH = minOf((curr.bottom - curr.top) * 0.5f, (next.bottom - next.top) * 0.5f)
                        val effR = minOf(r, diff * 0.5f, maxRByH)
                        val effK = effR * 0.55228475f

                        if (next.left > curr.left + 0.5f) {
                            p.lineTo(curr.left, curr.bottom - effR)
                            p.bezierTo(
                                curr.left, curr.bottom - effR + effK,
                                curr.left + effR - effK, curr.bottom,
                                curr.left + effR, curr.bottom
                            )
                            p.lineTo(next.left - effR, curr.bottom)
                            p.bezierTo(
                                next.left - effR + effK, curr.bottom,
                                next.left, curr.bottom + effR - effK,
                                next.left, curr.bottom + effR
                            )
                        } else if (next.left < curr.left - 0.5f) {
                            p.lineTo(curr.left, curr.bottom - effR)
                            p.bezierTo(
                                curr.left, curr.bottom - effR + effK,
                                curr.left - effR + effK, curr.bottom,
                                curr.left - effR, curr.bottom
                            )
                            p.lineTo(next.left + effR, curr.bottom)
                            p.bezierTo(
                                next.left + effR - effK, curr.bottom,
                                next.left, curr.bottom + effR - effK,
                                next.left, curr.bottom + effR
                            )
                        } else {
                            p.lineTo(curr.left, curr.bottom)
                        }
                    } else {
                        val botEffR = minOf(r, (fixedRight - curr.left) * 0.5f, (curr.bottom - curr.top) * 0.5f)
                        val botK = botEffR * 0.55228475f
                        p.lineTo(curr.left, curr.bottom - botEffR)
                        p.bezierTo(
                            curr.left, curr.bottom - botEffR + botK,
                            curr.left + botEffR - botK, curr.bottom,
                            curr.left + botEffR, curr.bottom
                        )
                        p.lineTo(fixedRight, curr.bottom)
                    }
                }

                p.lineTo(fixedRight, firstRow.top)
                p.close()
            } else {
                val firstRow = activeRows.first()
                p.moveTo(fixedLeft, firstRow.top)

                val topEffR = minOf(r, (firstRow.right - fixedLeft) * 0.5f, (firstRow.bottom - firstRow.top) * 0.5f)
                val topK = topEffR * 0.55228475f
                p.lineTo(firstRow.right - topEffR, firstRow.top)

                p.bezierTo(
                    firstRow.right - topEffR + topK, firstRow.top,
                    firstRow.right, firstRow.top + topEffR - topK,
                    firstRow.right, firstRow.top + topEffR
                )

                for (i in activeRows.indices) {
                    val curr = activeRows[i]
                    if (i < activeRows.size - 1) {
                        val next = activeRows[i + 1]
                        val diff = kotlin.math.abs(next.right - curr.right)
                        val maxRByH = minOf((curr.bottom - curr.top) * 0.5f, (next.bottom - next.top) * 0.5f)
                        val effR = minOf(r, diff * 0.5f, maxRByH)
                        val effK = effR * 0.55228475f

                        if (next.right < curr.right - 0.5f) {
                            p.lineTo(curr.right, curr.bottom - effR)
                            p.bezierTo(
                                curr.right, curr.bottom - effR + effK,
                                curr.right - effR + effK, curr.bottom,
                                curr.right - effR, curr.bottom
                            )
                            p.lineTo(next.right + effR, curr.bottom)
                            p.bezierTo(
                                next.right + effR - effK, curr.bottom,
                                next.right, curr.bottom + effR - effK,
                                next.right, curr.bottom + effR
                            )
                        } else if (next.right > curr.right + 0.5f) {
                            p.lineTo(curr.right, curr.bottom - effR)
                            p.bezierTo(
                                curr.right, curr.bottom - effR + effK,
                                curr.right + effR - effK, curr.bottom,
                                curr.right + effR, curr.bottom
                            )
                            p.lineTo(next.right - effR, curr.bottom)
                            p.bezierTo(
                                next.right - effR + effK, curr.bottom,
                                next.right, curr.bottom + effR - effK,
                                next.right, curr.bottom + effR
                            )
                        } else {
                            p.lineTo(curr.right, curr.bottom)
                        }
                    } else {
                        val botEffR = minOf(r, (curr.right - fixedLeft) * 0.5f, (curr.bottom - curr.top) * 0.5f)
                        val botK = botEffR * 0.55228475f
                        p.lineTo(curr.right, curr.bottom - botEffR)
                        p.bezierTo(
                            curr.right, curr.bottom - botEffR + botK,
                            curr.right - botEffR + botK, curr.bottom,
                            curr.right - botEffR, curr.bottom
                        )
                        p.lineTo(fixedLeft, curr.bottom)
                    }
                }

                p.lineTo(fixedLeft, firstRow.top)
                p.close()
            }
        }

        if (HUDModule.widgetShadow.value && HUDModule.shadowStrength.getFloat() > 0.001f) {
            val shadowStrength = HUDModule.shadowStrength.getFloat()
            val shadowSize = 8f
            val baseAlpha = (0.42f * shadowStrength).coerceIn(0f, 1f)
            val passes = 5
            val baseShadowColor = HUDModule.getShadowColor(getAccentColor(0f))
            for (step in passes downTo 1) {
                val frac = step.toFloat() / passes.toFloat()
                val strokeWidth = shadowSize * frac * 2f
                val stepAlpha = (baseAlpha * (1f - frac * 0.65f) * 0.16f).coerceIn(0f, 1f)
                buildFilletedPath(path)
                path.stroke(baseShadowColor.withAlpha(stepAlpha), strokeWidth)
            }
        }

        val alpha = HUDModule.bgOpacity.getFloat().coerceIn(0f, 1f)
        val bgColor = NanoVGColor(0f, 0f, 0f, alpha)

        if (shouldBlur) {
            try {
                RenderEngine.backdropBlurRenderer.withBlurPaint(
                    HUDModule.blurRadius.getFloat(), 1f,
                    Rect2f(
                        activeRows.minOf { it.left }, activeRows.first().top,
                        activeRows.maxOf { it.right } - activeRows.minOf { it.left },
                        activeRows.last().bottom - activeRows.first().top
                    )
                ) { paint ->
                    buildFilletedPath(path)
                    org.lwjgl.nanovg.NanoVG.nvgFillPaint(RenderEngine.nanoVG.handle, paint)
                    org.lwjgl.nanovg.NanoVG.nvgFill(RenderEngine.nanoVG.handle)
                }
            } catch (ignored: Throwable) {}
        }

        buildFilletedPath(path)
        path.fill(bgColor)

        if (outline.value) {
            buildFilletedPath(path)
            path.stroke(NanoVGColor(1f, 1f, 1f, 0.08f), 0.75f)
        }
    }

    if (hasLeftBar || hasRightBar) {
        val totalRows = activeRows.size
        for (i in 0 until totalRows) {
            val curr = activeRows[i]
            val mainColor = resolveColor(curr.index, entries.size, curr.slide)
            val isFirst = i == 0
            val isLast = i == totalRows - 1
            val rowH = curr.bottom - curr.top
            val next = if (i < totalRows - 1) activeRows[i + 1] else null
            val nextColor = if (next != null) resolveColor(next.index, entries.size, next.slide) else mainColor
            val seamOverlap = if (!isLast) 0.35f else 0f

            if (hasLeftBar) {
                val barX = curr.left
                val barY = curr.top
                val barHeight = rowH + seamOverlap

                val radii = when {
                    isFirst && isLast -> CornerRadii(minOf(r, barW), 0f, 0f, minOf(r, barW))
                    isFirst -> CornerRadii(minOf(r, barW), 0f, 0f, 0f)
                    isLast -> CornerRadii(0f, 0f, 0f, minOf(r, barW))
                    else -> CornerRadii.ZERO
                }
                Draw2D.roundedRect(barX, barY, barW, barHeight, radii, mainColor)

                if (rightSide && next != null && kotlin.math.abs(next.left - curr.left) > 0.5f) {
                    val stepX = minOf(curr.left, next.left)
                    val stepW = kotlin.math.abs(next.left - curr.left) + barW
                    val stepY = curr.bottom - barW
                    val stepColor = mainColor.lerp(nextColor, 0.5f)
                    Draw2D.rect(stepX, stepY, stepW, barW + 0.35f, stepColor)
                }
            }

            if (hasRightBar) {
                val barX = curr.right - barW
                val barY = curr.top
                val barHeight = rowH + seamOverlap

                val radii = when {
                    isFirst && isLast -> CornerRadii(0f, minOf(r, barW), minOf(r, barW), 0f)
                    isFirst -> CornerRadii(0f, minOf(r, barW), 0f, 0f)
                    isLast -> CornerRadii(0f, 0f, minOf(r, barW), 0f)
                    else -> CornerRadii.ZERO
                }
                Draw2D.roundedRect(barX, barY, barW, barHeight, radii, mainColor)

                if (!rightSide && next != null && kotlin.math.abs(next.right - curr.right) > 0.5f) {
                    val stepX = minOf(curr.right - barW, next.right - barW)
                    val stepW = kotlin.math.abs(next.right - curr.right) + barW
                    val stepY = curr.bottom - barW
                    val stepColor = mainColor.lerp(nextColor, 0.5f)
                    Draw2D.rect(stepX, stepY, stepW, barW + 0.35f, stepColor)
                }
            }
        }
    }

    for (row in activeRows) {
        val entry = row.entry
        val slide = row.slide
        val mainColor = resolveColor(row.index, entries.size, slide)
        val suffixColor = modernSuffixColor.multiplyAlpha(slide)

        val rowH = row.bottom - row.top
        val textTop = row.top + (rowH - fs) * 0.5f

        if (slide in 0.02f..0.99f) {
            val dir = if (rightSide) 1f else -1f
            val rowVx = (1f - slide) * 24f * dir
            cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                row.left, row.top, (row.right - row.left).coerceAtLeast(1f), rowH,
                cornerRadius.getFloat(),
                rowVx,
                mainColor.multiplyAlpha(0.28f * slide),
                maxBlur = 14f
            )
        }

        val textBlur = cn.reverie.render.animation.MotionBlurHelper.getBlurForProgress(slide, maxBlur = 5.5f)

        val rowClipW = (row.right - row.left).coerceAtLeast(0f)
        if (rowClipW > 0.5f && rowH > 0.5f) {

            NanoVGRenderer.withScissor(row.left, row.top, rowClipW, rowH) {
                if (rightSide) {
                    val textRight = row.right - padX - rightBarPad
                    if (entry.suffix != null) {
                        font.drawString(entry.suffix, textRight, textTop, suffixColor, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        if (fadeMode) {
                            font.drawHorizontalFadeString(entry.mainName, textRight - entry.suffixWidth, textTop, fs, slide, TextAlignment.TOP_RIGHT, textShadow, textBlur)
                        } else {
                            font.drawString(entry.mainName, textRight - entry.suffixWidth, textTop, mainColor, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        }
                    } else {
                        if (fadeMode) {
                            font.drawHorizontalFadeString(entry.totalText, textRight, textTop, fs, slide, TextAlignment.TOP_RIGHT, textShadow, textBlur)
                        } else {
                            font.drawString(entry.totalText, textRight, textTop, mainColor, fs, TextAlignment.TOP_RIGHT, textShadow, blur = textBlur)
                        }
                    }
                } else {
                    var textX = row.left + padX + leftBarPad
                    if (entry.suffix != null) {
                        textX += if (fadeMode) {
                            font.drawHorizontalFadeString(entry.mainName, textX, textTop, fs, slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                        } else {
                            font.drawString(entry.mainName, textX, textTop, mainColor, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
                        }
                        font.drawString(entry.suffix, textX, textTop, suffixColor, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
                    } else {
                        if (fadeMode) {
                            font.drawHorizontalFadeString(entry.totalText, textX, textTop, fs, slide, TextAlignment.TOP_LEFT, textShadow, textBlur)
                        } else {
                            font.drawString(entry.totalText, textX, textTop, mainColor, fs, TextAlignment.TOP_LEFT, textShadow, blur = textBlur)
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
