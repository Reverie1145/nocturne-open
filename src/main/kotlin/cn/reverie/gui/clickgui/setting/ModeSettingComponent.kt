package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.ClickGUIModule
class ModeSettingComponent(val modeSetting: ModeSetting) : SettingComponent(modeSetting) {

    private data class ModeBox(val mode: String, val x: Float, val y: Float, val width: Float, val height: Float)
    private val boxList = mutableListOf<ModeBox>()

    private var pillX = 0f
    private var pillY = 0f
    private var pillW = 0f
    private var pillH = 0f
    private var isPillInitialized = false
    private var prevPillX = Float.NaN
    private var prevPillY = Float.NaN
    private var selectedTextBlur = 0f

    private val previewsHudFonts: Boolean
        get() = modeSetting === HUDModule.fontChoice

    private fun <T> withModeFont(mode: String, block: () -> T): T {
        if (!previewsHudFonts) return block()
        val font = RenderEngine.getFontByName(mode) ?: return block()
        return RenderEngine.fontRenderer.withFont(font, block = block)
    }

    private fun modeTextWidth(mode: String, fontSize: Float): Float =
        withModeFont(mode) { NanoVGRenderer.getTextWidth(mode, fontSize) }

    init {
        updateDynamicHeight()
    }

    private fun updateDynamicHeight() {
        val maxW = if (width > 20f) width - 16f else 114f
        val titleH = 14f
        val boxH = 16f
        val gap = 3.5f

        val modes = modeSetting.modes
        if (modes.isEmpty()) {
            height = titleH + 2f
            return
        }

        val maxTextW = modes.maxOfOrNull { modeTextWidth(it, 9.0f) } ?: 30f
        val minBoxWNeeded = maxTextW + 12f

        val cols = when {
            modes.size % 3 == 0 && (3 * minBoxWNeeded + 2 * gap <= maxW) -> 3
            modes.size >= 2 && (2 * minBoxWNeeded + gap <= maxW) -> 2
            else -> 1
        }

        val rows = (modes.size + cols - 1) / cols
        height = titleH + rows * (boxH + gap) + 4f
    }

    private fun computeLayout() {
        boxList.clear()
        val startPadX = x + 8f
        val maxW = width - 16f
        val titleH = 14f
        val boxH = 16f
        val gap = 3.5f

        val modes = modeSetting.modes
        if (modes.isEmpty()) {
            height = titleH + 2f
            return
        }

        val maxTextW = modes.maxOfOrNull { modeTextWidth(it, 9.0f) } ?: 30f
        val minBoxWNeeded = maxTextW + 12f

        val cols = when {
            modes.size % 3 == 0 && (3 * minBoxWNeeded + 2 * gap <= maxW) -> 3
            modes.size >= 2 && (2 * minBoxWNeeded + gap <= maxW) -> 2
            else -> 1
        }

        val colW = (maxW - (cols - 1) * gap) / cols.toFloat()
        val startY = y + titleH + 1f

        for (i in modes.indices) {
            val col = i % cols
            val row = i / cols
            val bx = startPadX + col * (colW + gap)
            val by = startY + row * (boxH + gap)
            boxList.add(ModeBox(modes[i], bx, by, colW, boxH))
        }

        val rows = (modes.size + cols - 1) / cols
        height = titleH + rows * (boxH + gap) + 4f
    }

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        computeLayout()

        val maxTitleW = width - 16f
        val displayTitle = RenderEngine.fontRenderer.trimWithEllipsis(modeSetting.name, maxTitleW, 10f)
        NanoVGRenderer.drawVerticallyCenteredString(
            displayTitle,
            x + 8f,
            y + 7.0f,
            NanoVGColor(0.70f, 0.72f, 0.78f, 0.85f),
            10f
        )

        val targetBox = boxList.find { modeSetting.isMode(it.mode) } ?: boxList.firstOrNull()

        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        val lerpFactor = (0.16f * animMultiplier).coerceIn(0.06f, 1.0f)

        if (targetBox != null) {
            if (!isPillInitialized || pillW <= 0f) {
                pillX = targetBox.x
                pillY = targetBox.y
                pillW = targetBox.width
                pillH = targetBox.height
                isPillInitialized = true
            } else {
                val dx = targetBox.x - pillX
                val dy = targetBox.y - pillY
                val dw = targetBox.width - pillW
                val dh = targetBox.height - pillH
                if (kotlin.math.abs(dx) < 0.05f && kotlin.math.abs(dy) < 0.05f && kotlin.math.abs(dw) < 0.05f && kotlin.math.abs(dh) < 0.05f) {
                    pillX = targetBox.x
                    pillY = targetBox.y
                    pillW = targetBox.width
                    pillH = targetBox.height
                } else {
                    pillX += dx * lerpFactor
                    pillY += dy * lerpFactor
                    pillW += dw * lerpFactor
                    pillH += dh * lerpFactor
                }
            }
        }

        val pillVx = if (prevPillX.isNaN()) 0f else (pillX - prevPillX)
        val pillVy = if (prevPillY.isNaN()) 0f else (pillY - prevPillY)
        prevPillX = pillX
        prevPillY = pillY
        val pillSpeed = kotlin.math.sqrt(pillVx * pillVx + pillVy * pillVy)

        for (box in boxList) {
            val isHovered = mouseX in box.x..(box.x + box.width) && mouseY in box.y..(box.y + box.height)
            if (isHovered) {
                NanoVGRenderer.drawRoundedRect(box.x, box.y, box.width, box.height, 2.5f, NanoVGColor(1f, 1f, 1f, 0.05f))
            } else {
                NanoVGRenderer.drawRoundedRect(box.x, box.y, box.width, box.height, 2.5f, NanoVGColor(0f, 0f, 0f, 0.15f))
            }
        }

        if (isPillInitialized && pillW > 0f) {
            val pillRadius = 2.5f

            if (pillSpeed > 0.04f) {
                cn.reverie.render.animation.MotionBlurHelper.renderMotionBlur(
                    pillX, pillY, pillW, pillH, pillRadius,
                    pillVx, pillVy,
                    NanoVGColor(1f, 1f, 1f, 0.35f),
                    maxBlur = 16f,
                    samples = 4
                )
            }

            NanoVGRenderer.drawRoundedRect(pillX, pillY, pillW, pillH, pillRadius, NanoVGColor(1f, 1f, 1f, 0.12f))

            NanoVGRenderer.drawRoundedOutline(
                pillX, pillY, pillW, pillH,
                pillRadius,
                NanoVGColor(1f, 1f, 1f, 0.12f),
                0.75f
            )
        }

        for (box in boxList) {
            val isSelected = modeSetting.isMode(box.mode)
            val isHovered = mouseX in box.x..(box.x + box.width) && mouseY in box.y..(box.y + box.height)

            val textColor = when {
                isSelected -> NanoVGColor.WHITE
                isHovered -> NanoVGColor(0.90f, 0.92f, 0.96f, 0.95f)
                else -> NanoVGColor(0.58f, 0.60f, 0.68f, 0.85f)
            }

            if (isSelected) {
                if (pillSpeed > 0.04f) {
                    selectedTextBlur = (pillSpeed * 0.70f).coerceIn(selectedTextBlur, 5.2f)
                } else {
                    selectedTextBlur += (0f - selectedTextBlur) * 0.09f
                    if (selectedTextBlur < 0.04f) selectedTextBlur = 0f
                }
            }
            val textBlur = if (isSelected) selectedTextBlur else 0f

            withModeFont(box.mode) {
                val maxTextW = box.width - 6f
                var fontSize = 9.0f
                var textW = NanoVGRenderer.getTextWidth(box.mode, fontSize)
                if (textW > maxTextW) {
                    fontSize = 8.0f
                    textW = NanoVGRenderer.getTextWidth(box.mode, fontSize)
                }
                val displayText = if (textW > maxTextW) {
                    RenderEngine.fontRenderer.trimWithEllipsis(box.mode, maxTextW, fontSize)
                } else {
                    box.mode
                }

                NanoVGRenderer.withScissor(box.x + 0.5f, box.y, box.width - 1.0f, box.height) {
                    NanoVGRenderer.drawCenteredString(
                        displayText,
                        box.x + box.width * 0.5f,
                        box.y + box.height * 0.5f,
                        textColor,
                        fontSize,
                        shadow = HUDModule.textShadow.value,
                        blur = textBlur
                    )
                }
            }
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button == 0) {
            for (box in boxList) {
                if (mouseX in box.x..(box.x + box.width) && mouseY in box.y..(box.y + box.height)) {
                    modeSetting.value = box.mode
                    return true
                }
            }
        }
        return false
    }
}
