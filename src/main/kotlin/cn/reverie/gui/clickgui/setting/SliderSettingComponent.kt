package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.math.MathUtils
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.ClickGUIModule
class SliderSettingComponent(val numSetting: NumberSetting) : SettingComponent(numSetting) {

    init {
        height = 24f
    }

    private var isDragging = false
    private val normValue: Float
        get() = ((numSetting.value - numSetting.min) / (numSetting.max - numSetting.min)).toFloat().coerceIn(0f, 1f)

    private val thumbAnim = FloatAnimation(normValue, normValue, 160L, CubicBezier.EASE_OUT)
    private val hoverDragAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    private val bubbleFadeAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT)
    private var prevThumbX: Float = Float.NaN

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        thumbAnim.durationMs = (160L / animMultiplier).toLong().coerceIn(40L, 400L)
        thumbAnim.animateTo(normValue)

        val isInteracting = isDragging || isMouseOver(mouseX, mouseY)
        hoverDragAnim.durationMs = (180L / animMultiplier).toLong().coerceIn(40L, 400L)
        hoverDragAnim.animateTo(if (isInteracting) 1f else 0f)
        bubbleFadeAnim.durationMs = (240L / animMultiplier).toLong().coerceIn(60L, 500L)
        bubbleFadeAnim.animateTo(if (isDragging) 1f else 0f)

        val stepStr = if (numSetting.step >= 1.0) "${numSetting.step.toInt()}" else "${numSetting.step}"
        val valueStr = if (numSetting.step >= 1.0) {
            numSetting.getInt().toString()
        } else {
            MathUtils.roundToDecimal(numSetting.getFloat(), 2).toString()
        }

        val displayVal = if (isDragging) "$valueStr (±$stepStr)" else valueStr
        val displayValW = NanoVGRenderer.getTextWidth(displayVal, 9.5f)

        val centerY = y + 7.0f

        val valueRightX = x + width - 6f
        val idleValColor = NanoVGColor(0.55f, 0.58f, 0.68f, 0.85f)
        val activeValColor = NanoVGColor(0.98f, 0.98f, 1f, 1f)
        val valueColor = idleValColor.lerp(activeValColor, hoverDragAnim.value)
        NanoVGRenderer.drawRightVerticallyCenteredString(displayVal, valueRightX, centerY, valueColor, 9.5f)

        val maxTextW = (valueRightX - displayValW) - (x + 8f) - 4f
        NanoVGRenderer.drawMarqueeString(
            numSetting.name,
            x + 8f,
            centerY,
            maxTextW,
            isMouseOver(mouseX, mouseY),
            NanoVGColor(0.75f, 0.78f, 0.85f, 0.90f),
            10.0f
        )

        val trackX = x + 8f
        val trackW = width - 16f
        val trackH = 2.0f + 0.6f * hoverDragAnim.value
        val trackY = y + 17.5f - trackH * 0.5f

        NanoVGRenderer.drawRoundedRect(trackX, trackY, trackW, trackH, trackH * 0.5f, NanoVGColor(0f, 0f, 0f, 0.25f))

        val fillWidth = (trackW * thumbAnim.value).coerceIn(0f, trackW)
        if (fillWidth > 0f) {
            val barColor = NanoVGColor(0.85f, 0.88f, 0.95f, 0.95f).lerp(NanoVGColor.WHITE, hoverDragAnim.value)
            NanoVGRenderer.drawRoundedRect(trackX, trackY, fillWidth, trackH, trackH * 0.5f, barColor)
        }

        val thumbX = trackX + fillWidth
        val thumbY = trackY + trackH * 0.5f
        val thumbR = 2.5f + 1.3f * hoverDragAnim.value

        val thumbVx = if (prevThumbX.isNaN()) 0f else (thumbX - prevThumbX)
        prevThumbX = thumbX

        if (kotlin.math.abs(thumbVx) > 0.03f) {
            cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                thumbX - thumbR,
                thumbY - thumbR,
                thumbR * 2f,
                thumbR * 2f,
                thumbR,
                thumbVx,
                NanoVGColor(1f, 1f, 1f, 0.42f),
                maxBlur = 12f
            )
        }

        if (hoverDragAnim.value > 0.05f) {
            NanoVGRenderer.drawCircle(thumbX, thumbY, thumbR + 1.8f * hoverDragAnim.value, NanoVGColor(1f, 1f, 1f, 0.12f * hoverDragAnim.value))
        }
        NanoVGRenderer.drawCircle(thumbX, thumbY, thumbR, NanoVGColor(0.98f, 0.98f, 1f, 1f))

        if (bubbleFadeAnim.value > 0.01f) {
            val bp = bubbleFadeAnim.value
            val badgeText = "$valueStr · step $stepStr"
            val badgeW = NanoVGRenderer.getTextWidth(badgeText, 8.5f) + 10f
            val badgeH = 13f
            val badgeX = (thumbX - badgeW * 0.5f).coerceIn(x + 2f, x + width - badgeW - 2f)
            val badgeY = trackY - 15f * bp - 2f * (1f - bp)

            if (HUDModule.widgetShadow.value) Draw2D.shadow(badgeX, badgeY, badgeW, badgeH, 3f, 6f, NanoVGColor(0f, 0f, 0f, 0.45f * bp * HUDModule.shadowStrength.getFloat()))
            NanoVGRenderer.drawRoundedRect(badgeX, badgeY, badgeW, badgeH, 2.5f, NanoVGColor(0.12f, 0.13f, 0.16f, 0.96f * bp))
            NanoVGRenderer.drawRoundedOutline(badgeX, badgeY, badgeW, badgeH, 2.5f, NanoVGColor(1f, 1f, 1f, 0.18f * bp), 0.75f)
            NanoVGRenderer.drawBlurTransitionCentered(badgeText, badgeX + badgeW * 0.5f, badgeY + badgeH * 0.5f, NanoVGColor.WHITE, 8.5f, progress = bp, maxBlur = 5.0f)
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isMouseOver(mouseX, mouseY) && button == 0) {
            isDragging = true
            updateValue(mouseX)
            return true
        }
        return false
    }

    override fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val was = isDragging
        isDragging = false
        return was
    }

    override fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        if (isDragging) {
            updateValue(mouseX)
            return true
        }
        return false
    }

    private fun updateValue(mouseX: Float) {
        val trackX = x + 8f
        val trackW = width - 16f
        val pct = ((mouseX - trackX) / trackW).coerceIn(0f, 1f)
        val raw = numSetting.min + pct * (numSetting.max - numSetting.min)
        numSetting.setClamped(raw)
    }
}
