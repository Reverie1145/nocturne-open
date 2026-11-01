package cn.reverie.gui.clickgui.setting
import cn.reverie.module.*
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.gui.clickgui.ClickGUIModule
class BooleanSettingComponent(
    val boolSetting: BooleanSetting,
    val isMetaSetting: Boolean = false
) : SettingComponent(boolSetting) {

    init {
        height = 19f
    }

    private val toggleAnim = FloatAnimation(
        if (boolSetting.value) 1f else 0f,
        if (boolSetting.value) 1f else 0f,
        280L,
        CubicBezier.EASE_OUT
    )
    private val hoverAnim = FloatAnimation(0f, 0f, 180L, CubicBezier.EASE_OUT)
    private var prevKnobX: Float = Float.NaN

    override fun render(mouseX: Float, mouseY: Float, partialTicks: Float) {
        val animMultiplier = ClickGUIModule.animSpeed.getFloat()
        toggleAnim.durationMs = (280L / animMultiplier).toLong().coerceIn(60L, 800L)
        hoverAnim.durationMs = (180L / animMultiplier).toLong().coerceIn(40L, 400L)
        toggleAnim.animateTo(if (boolSetting.value) 1f else 0f)
        hoverAnim.animateTo(if (isMouseOver(mouseX, mouseY)) 1f else 0f)

        val isHidden = isMetaSetting || boolSetting.name.equals("Hidden", ignoreCase = true)
        val centerY = y + height * 0.5f

        if (isHidden) {
            Draw2D.line(x + 4f, y, x + width - 4f, y, NanoVGColor(1f, 1f, 1f, 0.05f), 1.0f)
        }

        val p = toggleAnim.value.coerceIn(0f, 1f)

        val switchW = 19.5f + 1.0f * p
        val switchH = 10.2f + 0.6f * p
        val switchX = x + width - switchW - 6f
        val switchY = centerY - switchH * 0.5f
        val radius = switchH * 0.5f

        val maxTextW = switchX - (x + 8f) - 4f
        val displayName = if (isHidden) "Hidden (HUD)" else boolSetting.name
        val idleTextColor = if (isHidden) NanoVGColor(0.50f, 0.52f, 0.60f, 0.70f) else NanoVGColor(0.70f, 0.72f, 0.80f, 0.85f)
        val activeTextColor = if (isHidden) NanoVGColor(0.80f, 0.82f, 0.90f, 0.90f) else NanoVGColor(0.95f, 0.96f, 0.98f, 1f)
        val textColor = idleTextColor.lerp(activeTextColor, p)
        val textBlur = if (toggleAnim.isRunning) kotlin.math.sin(p * Math.PI).toFloat() * 3.5f else 0f

        NanoVGRenderer.drawMarqueeString(
            displayName,
            x + 8f,
            centerY,
            maxTextW,
            isMouseOver(mouseX, mouseY),
            textColor,
            if (isHidden) 9.5f else 10.0f,
            shadow = HUDModule.textShadow.value,
            blur = textBlur
        )

        val offBg = NanoVGColor(0f, 0f, 0f, 0.25f)
        val onBg = if (isHidden) NanoVGColor(0.18f, 0.20f, 0.24f, 0.95f) else NanoVGColor(0.24f, 0.25f, 0.30f, 0.95f)
        val hoverHighlight = NanoVGColor(1f, 1f, 1f, 0.04f * hoverAnim.value)
        val currentBg = offBg.lerp(onBg, p)

        NanoVGRenderer.drawRoundedRect(switchX, switchY, switchW, switchH, radius, currentBg)
        if (hoverAnim.value > 0.01f) {
            NanoVGRenderer.drawRoundedRect(switchX, switchY, switchW, switchH, radius, hoverHighlight)
        }

        val outlineAlpha = (0.04f + 0.10f * p + 0.04f * hoverAnim.value).coerceIn(0f, 0.18f)
        NanoVGRenderer.drawRoundedOutline(
            switchX, switchY, switchW, switchH, radius,
            NanoVGColor(1f, 1f, 1f, outlineAlpha),
            0.75f
        )

        val baseR = 2.6f + 1.2f * p
        val stretch = kotlin.math.sin(p * Math.PI).toFloat() * 1.8f
        val knobW = (baseR * 2f) + stretch
        val knobH = (baseR * 2f) - stretch * 0.25f

        val minKnobX = switchX + 2.6f + 1.2f
        val maxKnobX = switchX + switchW - 3.8f - 1.2f
        val knobCenterX = minKnobX + (maxKnobX - minKnobX) * p
        val knobCenterY = centerY

        val knobVx = if (prevKnobX.isNaN()) 0f else (knobCenterX - prevKnobX)
        prevKnobX = knobCenterX

        if (kotlin.math.abs(knobVx) > 0.03f) {
            cn.reverie.render.animation.MotionBlurHelper.renderHorizontalMotionBlur(
                knobCenterX - knobW * 0.5f,
                knobCenterY - knobH * 0.5f,
                knobW, knobH,
                knobH * 0.5f,
                knobVx,
                NanoVGColor(1f, 1f, 1f, 0.45f),
                maxBlur = 12f
            )
        }

        val knobColor = NanoVGColor(0.70f, 0.73f, 0.80f, 0.90f).lerp(NanoVGColor.WHITE, p)
        if (p > 0.25f) {
            NanoVGRenderer.drawCircle(knobCenterX, knobCenterY, baseR + 1.5f, NanoVGColor(1f, 1f, 1f, 0.12f * p))
        }
        NanoVGRenderer.drawRoundedRect(
            knobCenterX - knobW * 0.5f,
            knobCenterY - knobH * 0.5f,
            knobW, knobH,
            knobH * 0.5f,
            knobColor
        )
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (isMouseOver(mouseX, mouseY) && button == 0) {
            boolSetting.toggle()
            return true
        }
        return false
    }
}
