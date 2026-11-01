package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.gui.clickgui.ClickGUIModule
class ColorSettingComponent(val colorSetting: ColorSetting) : SettingComponent(colorSetting) {

    var isExpanded: Boolean = expandedMemory[colorSetting] ?: false
        set(value) {
            field = value
            expandedMemory[colorSetting] = value
        }
    private val expandAnim = FloatAnimation(
        if (isExpanded) 1f else 0f,
        if (isExpanded) 1f else 0f,
        280L,
        CubicBezier.EASE_OUT
    )

    private var isDraggingSV = false
    private var isDraggingHue = false
    private var isDraggingAlpha = false
    private var isDraggingSpeed = false

    internal var hsv = floatArrayOf(230f, 0.85f, 0.85f, 1.0f)
    private var lastSyncedColor: NanoVGColor? = null

    companion object {
        private val expandedMemory = mutableMapOf<ColorSetting, Boolean>()
    }

    private val PRESETS = arrayOf(
        NanoVGColor(1.00f, 1.00f, 1.00f, 1.0f),
        NanoVGColor(0.63f, 0.63f, 0.69f, 1.0f),
        NanoVGColor(0.37f, 0.42f, 0.82f, 1.0f),
        NanoVGColor(0.22f, 0.74f, 0.97f, 1.0f),
        NanoVGColor(0.20f, 0.83f, 0.60f, 1.0f),
        NanoVGColor(0.98f, 0.75f, 0.14f, 1.0f),
        NanoVGColor(0.96f, 0.25f, 0.37f, 1.0f)
    )

    init {
        syncHsvFromActive()
        height = 20f
    }

    private fun syncHsvFromActive() {
        val activeColor = colorSetting.activeColor
        hsv = activeColor.toHsv()
        lastSyncedColor = activeColor
    }

    internal fun noteColorChanged() {
        lastSyncedColor = colorSetting.activeColor
    }

    private val isGradientMode: Boolean
        get() = colorSetting.colorMode == ColorMode.GRADIENT_2 ||
                colorSetting.colorMode == ColorMode.GRADIENT_3 ||
                colorSetting.colorMode == ColorMode.GRADIENT_4

    private val contentExpandedHeight: Float
        get() = when {
            colorSetting.colorMode == ColorMode.RAINBOW -> 158f
            isGradientMode -> 126f
            else -> 108f
        }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (colorSetting.activeColor != lastSyncedColor) {
            syncHsvFromActive()
        }

        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        expandAnim.durationMs = (280L / animMultiplier).toLong().coerceIn(60L, 600L)
        expandAnim.animateTo(if (isExpanded) 1f else 0f)
        height = 20f + contentExpandedHeight * expandAnim.value

        val (col1, col2) = colorSetting.getColorPair()

        val boxW = 22f
        val boxH = 11f
        val rightPad = 6f
        val chevronW = 8f
        val boxX = x + width - boxW - rightPad - chevronW - 4f
        val boxY = y + (20f - boxH) * 0.5f

        if (colorSetting.colorMode == ColorMode.STATIC) {
            NanoVGRenderer.drawRoundedRect(boxX, boxY, boxW, boxH, 2.5f, colorSetting.primaryColor)
        } else {
            NanoVGRenderer.drawGradientRoundedRect(boxX, boxY, boxW, boxH, 2.5f, col1, col2, true)
        }
        NanoVGRenderer.drawRoundedOutline(boxX, boxY, boxW, boxH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.20f), 0.75f)

        val chevronX = x + width - rightPad - chevronW * 0.5f
        val chevronY = y + 10f
        CategoryVectorIcons.drawChevronRight(chevronX, chevronY, 6.5f, expandAnim.value, NanoVGColor(0.50f, 0.52f, 0.60f, 0.75f))

        val infoStr = when (colorSetting.colorMode) {
            ColorMode.STATIC -> colorSetting.primaryColor.toHex()
            ColorMode.RAINBOW -> "RAINBOW"
            else -> "GRADIENT"
        }
        val infoW = NanoVGRenderer.getTextWidth(infoStr, 8.5f)
        NanoVGRenderer.drawRightVerticallyCenteredString(infoStr, boxX - 5f, y + 10f, NanoVGColor(0.50f, 0.52f, 0.60f, 0.80f), 8.5f)

        val maxTextColorW = (boxX - 5f - infoW) - (x + 8f) - 4f
        NanoVGRenderer.drawMarqueeString(
            colorSetting.name,
            x + 8f,
            y + 10f,
            maxTextColorW,
            isMouseOver(mouseX, mouseY),
            NanoVGColor(0.75f, 0.78f, 0.85f, 0.90f),
            10.5f
        )

        if (expandAnim.value > 0.01f) {
            val drawerY = y + 20f
            val drawerH = contentExpandedHeight * expandAnim.value

            NanoVGRenderer.withScissor(x + 4f, drawerY, width - 8f, drawerH) {
                val padX = x + 8f
                val fullW = width - 16f
                var curY = drawerY + 2f

                val modeTabH = 13.5f
                val modes = arrayOf("Static", "Rainbow", "Gradient")
                val tabW = fullW / modes.size.toFloat()

                NanoVGRenderer.drawRoundedRect(padX, curY, fullW, modeTabH, 2.5f, NanoVGColor(0f, 0f, 0f, 0.18f))

                val activeModeIdx = when {
                    colorSetting.colorMode == ColorMode.STATIC -> 0
                    colorSetting.colorMode == ColorMode.RAINBOW -> 1
                    else -> 2
                }
                val activeTabX = padX + activeModeIdx * tabW
                NanoVGRenderer.drawRoundedRect(activeTabX, curY, tabW, modeTabH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.12f))
                NanoVGRenderer.drawRoundedOutline(activeTabX, curY, tabW, modeTabH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)

                for ((idx, modeName) in modes.withIndex()) {
                    val tx = padX + idx * tabW + tabW * 0.5f
                    val isActive = idx == activeModeIdx
                    NanoVGRenderer.drawCenteredString(
                        modeName,
                        tx,
                        curY + modeTabH * 0.5f,
                        if (isActive) NanoVGColor.WHITE else NanoVGColor(0.55f, 0.58f, 0.65f, 0.75f),
                        8.5f
                    )
                }
                curY += modeTabH + 4f

                if (isGradientMode) {
                    val stopH = 13f
                    val stopCount = when (colorSetting.colorMode) {
                        ColorMode.GRADIENT_3 -> 3
                        ColorMode.GRADIENT_4 -> 4
                        else -> 2
                    }
                    val stopW = (fullW - (stopCount - 1) * 3f) / stopCount.toFloat()

                    for (i in 0 until stopCount) {
                        val sx = padX + i * (stopW + 3f)
                        val isCurrentStop = colorSetting.activeStopIndex == i
                        val stopColor = colorSetting.colorStops.getOrElse(i) { colorSetting.value }

                        NanoVGRenderer.drawRoundedRect(sx, curY, stopW, stopH, 2f, if (isCurrentStop) NanoVGColor(1f, 1f, 1f, 0.12f) else NanoVGColor(0f, 0f, 0f, 0.18f))
                        if (isCurrentStop) {
                            NanoVGRenderer.drawRoundedOutline(sx, curY, stopW, stopH, 2f, NanoVGColor(1f, 1f, 1f, 0.14f), 0.75f)
                        }

                        NanoVGRenderer.drawCircle(sx + 7f, curY + stopH * 0.5f, 3f, stopColor)
                        NanoVGRenderer.drawString("Stop ${i + 1}", sx + 13f, curY + 2.5f, if (isCurrentStop) NanoVGColor.WHITE else NanoVGColor(0.60f, 0.62f, 0.68f, 0.85f), 8.5f)
                    }
                    curY += stopH + 4f
                }

                val svBoxH = 44f
                renderSVBox(padX, curY, fullW, svBoxH)
                curY += svBoxH + 4f

                val hueH = 7f
                renderHueBar(padX, curY, fullW, hueH)
                curY += hueH + 4f

                val alphaH = 7f
                renderAlphaBar(padX, curY, fullW, alphaH)
                curY += alphaH + 5f

                renderPresetPalette(padX, curY, fullW)
                curY += 11f + 5f

                if (colorSetting.colorMode == ColorMode.RAINBOW) {
                    val previewH = 11f
                    renderRainbowPreview(padX, curY, fullW, previewH)
                    curY += previewH + 5f

                    val speedH = 14f
                    val speedPct = ((colorSetting.flowSpeed - 0.2f) / 2.8f).coerceIn(0f, 1f)
                    renderMiniSlider("Speed", "${String.format("%.1f", colorSetting.flowSpeed)}x", speedPct, padX, curY, fullW, speedH)
                    curY += speedH + 4f

                    renderMiniSlider("Opacity", "${(hsv[3] * 100).toInt()}%", hsv[3], padX, curY, fullW, speedH)
                }
            }
        }
    }

    private fun renderSVBox(bx: Float, by: Float, bw: Float, bh: Float) {
        val pureHue = NanoVGColor.fromHsv(hsv[0], 1f, 1f, 1f)

        NanoVGRenderer.drawRoundedRect(bx, by, bw, bh, 3f, pureHue)

        NanoVGRenderer.drawGradientRoundedRect(bx, by, bw, bh, 3f, NanoVGColor.WHITE, NanoVGColor.WHITE.withAlpha(0f), true)

        NanoVGRenderer.drawGradientRoundedRect(bx, by, bw, bh, 3f, NanoVGColor.BLACK.withAlpha(0f), NanoVGColor.BLACK, false)

        NanoVGRenderer.drawRoundedOutline(bx, by, bw, bh, 3f, NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)

        val curX = (bx + hsv[1] * bw).coerceIn(bx + 3f, bx + bw - 3f)
        val curY = (by + (1f - hsv[2]) * bh).coerceIn(by + 3f, by + bh - 3f)

        NanoVGRenderer.drawCircle(curX, curY, 4.2f, NanoVGColor(0f, 0f, 0f, 0.40f))
        NanoVGRenderer.drawCircle(curX, curY, 3.2f, NanoVGColor.WHITE)
        NanoVGRenderer.drawCircle(curX, curY, 1.8f, colorSetting.activeColor.withAlpha(1f))
    }

    private fun renderHueBar(bx: Float, by: Float, bw: Float, bh: Float) {
        val stops = 6
        val segW = bw / stops.toFloat()
        val hueColors = arrayOf(
            NanoVGColor(1f, 0f, 0f, 1f),
            NanoVGColor(1f, 1f, 0f, 1f),
            NanoVGColor(0f, 1f, 0f, 1f),
            NanoVGColor(0f, 1f, 1f, 1f),
            NanoVGColor(0f, 0f, 1f, 1f),
            NanoVGColor(1f, 0f, 1f, 1f),
            NanoVGColor(1f, 0f, 0f, 1f)
        )

        for (i in 0 until stops) {
            val sx = bx + i * segW
            NanoVGRenderer.drawGradientRoundedRect(sx, by, segW + 0.5f, bh, 0f, hueColors[i], hueColors[i + 1], true)
        }

        NanoVGRenderer.drawRoundedOutline(bx, by, bw, bh, bh * 0.5f, NanoVGColor(1f, 1f, 1f, 0.15f), 0.75f)

        val thumbX = (bx + (hsv[0] / 360f) * bw).coerceIn(bx + bh * 0.5f, bx + bw - bh * 0.5f)
        NanoVGRenderer.drawCircle(thumbX, by + bh * 0.5f, bh * 0.55f, NanoVGColor(0f, 0f, 0f, 0.35f))
        NanoVGRenderer.drawCircle(thumbX, by + bh * 0.5f, bh * 0.45f, NanoVGColor.WHITE)
    }

    private fun renderAlphaBar(bx: Float, by: Float, bw: Float, bh: Float) {
        NanoVGRenderer.drawRoundedRect(bx, by, bw, bh, bh * 0.5f, NanoVGColor(0f, 0f, 0f, 0.25f))

        val currentFull = NanoVGColor.fromHsv(hsv[0], hsv[1], hsv[2], 1f)
        NanoVGRenderer.drawGradientRoundedRect(bx, by, bw, bh, bh * 0.5f, currentFull.withAlpha(0f), currentFull, true)
        NanoVGRenderer.drawRoundedOutline(bx, by, bw, bh, bh * 0.5f, NanoVGColor(1f, 1f, 1f, 0.12f), 0.75f)

        val thumbX = (bx + hsv[3] * bw).coerceIn(bx + bh * 0.5f, bx + bw - bh * 0.5f)
        NanoVGRenderer.drawCircle(thumbX, by + bh * 0.5f, bh * 0.55f, NanoVGColor(0f, 0f, 0f, 0.35f))
        NanoVGRenderer.drawCircle(thumbX, by + bh * 0.5f, bh * 0.45f, NanoVGColor.WHITE)
    }

    private fun renderPresetPalette(bx: Float, by: Float, bw: Float) {
        val count = PRESETS.size
        val gap = 3.5f
        val chipW = (bw - (count - 1) * gap) / count.toFloat()
        val chipH = 11f

        for (i in 0 until count) {
            val px = bx + i * (chipW + gap)
            val presetColor = PRESETS[i]
            val isCurrent = isCloseColor(presetColor, colorSetting.activeColor)

            NanoVGRenderer.drawRoundedRect(px, by, chipW, chipH, 2.5f, presetColor)
            if (isCurrent) {
                NanoVGRenderer.drawRoundedOutline(px - 0.5f, by - 0.5f, chipW + 1f, chipH + 1f, 3f, NanoVGColor.WHITE, 1.2f)
            } else {
                NanoVGRenderer.drawRoundedOutline(px, by, chipW, chipH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.14f), 0.75f)
            }
        }
    }

    private fun isCloseColor(c1: NanoVGColor, c2: NanoVGColor): Boolean {
        return kotlin.math.abs(c1.r - c2.r) < 0.05f &&
               kotlin.math.abs(c1.g - c2.g) < 0.05f &&
               kotlin.math.abs(c1.b - c2.b) < 0.05f
    }

    private fun renderRainbowPreview(bx: Float, by: Float, bw: Float, bh: Float) {
        val stops = 6
        val segW = bw / stops.toFloat()
        val time = (System.currentTimeMillis() * 0.0006f * colorSetting.flowSpeed) % 1.0f

        for (i in 0 until stops) {
            val h1 = ((i.toFloat() / stops + time) % 1.0f) * 360f
            val h2 = (((i + 1).toFloat() / stops + time) % 1.0f) * 360f
            val c1 = NanoVGColor.fromHsv(h1, 0.85f, 0.95f, hsv[3])
            val c2 = NanoVGColor.fromHsv(h2, 0.85f, 0.95f, hsv[3])
            NanoVGRenderer.drawGradientRoundedRect(bx + i * segW, by, segW + 0.5f, bh, 0f, c1, c2, true)
        }
        NanoVGRenderer.drawRoundedOutline(bx, by, bw, bh, 2.5f, NanoVGColor(1f, 1f, 1f, 0.15f), 0.75f)
    }

    private fun renderMiniSlider(label: String, valStr: String, pct: Float, bx: Float, by: Float, bw: Float, bh: Float) {
        NanoVGRenderer.drawString(label, bx, by + 1f, NanoVGColor(0.70f, 0.72f, 0.80f, 0.85f), 8.5f)
        val valW = NanoVGRenderer.getTextWidth(valStr, 8.5f)
        NanoVGRenderer.drawString(valStr, bx + bw - valW, by + 1f, NanoVGColor(0.50f, 0.52f, 0.60f, 0.80f), 8.5f)

        val trackY = by + 9.5f
        val trackH = 2.0f
        NanoVGRenderer.drawRoundedRect(bx, trackY, bw, trackH, 1.0f, NanoVGColor(0f, 0f, 0f, 0.25f))
        val fillW = bw * pct.coerceIn(0f, 1f)
        if (fillW > 0f) {
            NanoVGRenderer.drawRoundedRect(bx, trackY, fillW, trackH, 1.0f, NanoVGColor(0.85f, 0.88f, 0.95f, 0.95f))
        }
        NanoVGRenderer.drawCircle(bx + fillW, trackY + trackH * 0.5f, 2.8f, NanoVGColor.WHITE)
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (mouseY >= y && mouseY <= y + 20f && mouseX >= x && mouseX <= x + width) {
            if (button == 0 || button == 1) {
                isExpanded = !isExpanded
                return true
            }
        }

        if (isExpanded) {
            val drawerY = y + 20f
            val padX = x + 8f
            val fullW = width - 16f
            var curY = drawerY + 2f

            val modeTabH = 13.5f
            if (mouseY in curY..(curY + modeTabH) && mouseX in padX..(padX + fullW)) {
                val tabW = fullW / 3f
                val idx = ((mouseX - padX) / tabW).toInt().coerceIn(0, 2)
                when (idx) {
                    0 -> colorSetting.colorMode = ColorMode.STATIC
                    1 -> colorSetting.colorMode = ColorMode.RAINBOW
                    2 -> if (!isGradientMode) colorSetting.colorMode = ColorMode.GRADIENT_2
                }
                syncHsvFromActive()
                return true
            }
            curY += modeTabH + 4f

            if (isGradientMode) {
                val stopH = 13f
                val stopCount = when (colorSetting.colorMode) {
                    ColorMode.GRADIENT_3 -> 3
                    ColorMode.GRADIENT_4 -> 4
                    else -> 2
                }
                val stopW = (fullW - (stopCount - 1) * 3f) / stopCount.toFloat()

                if (mouseY in curY..(curY + stopH) && mouseX in padX..(padX + fullW)) {
                    for (i in 0 until stopCount) {
                        val sx = padX + i * (stopW + 3f)
                        if (mouseX in sx..(sx + stopW)) {
                            colorSetting.activeStopIndex = i
                            syncHsvFromActive()
                            return true
                        }
                    }
                }
                curY += stopH + 4f
            }

            val svBoxH = 44f
            if (mouseX in padX..(padX + fullW) && mouseY in curY..(curY + svBoxH)) {
                isDraggingSV = true
                updateSV(mouseX, mouseY, padX, curY, fullW, svBoxH)
                return true
            }
            curY += svBoxH + 4f

            val hueH = 7f
            if (mouseX in padX..(padX + fullW) && mouseY in (curY - 1f)..(curY + hueH + 1f)) {
                isDraggingHue = true
                updateHue(mouseX, padX, fullW)
                return true
            }
            curY += hueH + 4f

            val alphaH = 7f
            if (mouseX in padX..(padX + fullW) && mouseY in (curY - 1f)..(curY + alphaH + 1f)) {
                isDraggingAlpha = true
                updateAlpha(mouseX, padX, fullW)
                return true
            }
            curY += alphaH + 5f

            val chipH = 11f
            val count = PRESETS.size
            val gap = 3.5f
            val chipW = (fullW - (count - 1) * gap) / count.toFloat()
            if (mouseY in curY..(curY + chipH) && mouseX in padX..(padX + fullW)) {
                for (i in 0 until count) {
                    val px = padX + i * (chipW + gap)
                    if (mouseX in px..(px + chipW)) {
                        val picked = PRESETS[i].withAlpha(hsv[3])
                        colorSetting.setActiveColor(picked)
                        syncHsvFromActive()
                        return true
                    }
                }
            }
            curY += chipH + 5f

            if (colorSetting.colorMode == ColorMode.RAINBOW) {
                val previewH = 11f
                curY += previewH + 5f

                val speedH = 14f
                if (mouseY in curY..(curY + speedH) && mouseX in padX..(padX + fullW)) {
                    isDraggingSpeed = true
                    updateSpeed(mouseX, padX, fullW)
                    return true
                }
                curY += speedH + 4f

                if (mouseY in curY..(curY + speedH) && mouseX in padX..(padX + fullW)) {
                    isDraggingAlpha = true
                    updateAlpha(mouseX, padX, fullW)
                    return true
                }
            }
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        isDraggingSV = false
        isDraggingHue = false
        isDraggingAlpha = false
        isDraggingSpeed = false
        return false
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (!isExpanded) return false

        val drawerY = y + 20f
        val padX = x + 8f
        val fullW = width - 16f
        var curY = drawerY + 2f + 13.5f + 4f

        if (isGradientMode) {
            curY += 13f + 4f
        }

        val svBoxH = 44f
        if (isDraggingSV) {
            updateSV(mouseX, mouseY, padX, curY, fullW, svBoxH)
            return true
        }
        curY += svBoxH + 4f

        if (isDraggingHue) {
            updateHue(mouseX, padX, fullW)
            return true
        }
        curY += 7f + 4f

        if (isDraggingAlpha) {
            updateAlpha(mouseX, padX, fullW)
            return true
        }
        curY += 7f + 5f
        curY += 11f + 5f

        if (colorSetting.colorMode == ColorMode.RAINBOW) {
            curY += 11f + 5f
            if (isDraggingSpeed) {
                updateSpeed(mouseX, padX, fullW)
                return true
            }
            curY += 14f + 4f
        }
        return false
    }

}
