package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.nanovg.NanoVGTransform
import net.minecraft.client.Minecraft

private class KeyCapAnim(val label: String) {
    val pressAnim = FloatAnimation(0f, 0f, 80L, CubicBezier.EASE_OUT)
    val clickTimestamps = mutableListOf<Long>()

    fun recordClick() {
        clickTimestamps.add(System.currentTimeMillis())
    }

    fun getCps(): Int {
        val now = System.currentTimeMillis()
        clickTimestamps.removeAll { now - it > 1000L }
        return clickTimestamps.size
    }
}

class Keystrokes : HudElement(
    name = "Keystrokes",
    defaultX = 12f,
    defaultY = 140f,
    width = 78f,
    height = 104f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.LEFT,
    verticalAnchor = HudVerticalAnchor.TOP
) {
    val showMouse = BooleanSetting("Mouse Keys", "Show LMB and RMB buttons", true)
    val showSpace = BooleanSetting("Spacebar", "Show jump space bar", true)
    val showCPS = BooleanSetting("Show CPS", "Show clicks per second on mouse buttons", true)

    private val keyW = KeyCapAnim("W")
    private val keyA = KeyCapAnim("A")
    private val keyS = KeyCapAnim("S")
    private val keyD = KeyCapAnim("D")
    private val keyLmb = KeyCapAnim("LMB")
    private val keyRmb = KeyCapAnim("RMB")
    private val keySpace = KeyCapAnim("SPACE")

    private var wasLmbDown = false
    private var wasRmbDown = false

    init {
        addSetting(showMouse)
        addSetting(showSpace)
        addSetting(showCPS)
    }

    override fun onTick() {
        super.onTick()

        val mc = Minecraft.getInstance()
        val opts = mc.options

        val isLmbDown = opts.keyAttack.isDown
        val isRmbDown = opts.keyUse.isDown

        if (isLmbDown && !wasLmbDown) keyLmb.recordClick()
        if (isRmbDown && !wasRmbDown) keyRmb.recordClick()
        wasLmbDown = isLmbDown
        wasRmbDown = isRmbDown

        keyW.pressAnim.animateTo(if (opts.keyUp.isDown) 1f else 0f)
        keyA.pressAnim.animateTo(if (opts.keyLeft.isDown) 1f else 0f)
        keyS.pressAnim.animateTo(if (opts.keyDown.isDown) 1f else 0f)
        keyD.pressAnim.animateTo(if (opts.keyRight.isDown) 1f else 0f)
        keyLmb.pressAnim.animateTo(if (isLmbDown) 1f else 0f)
        keyRmb.pressAnim.animateTo(if (isRmbDown) 1f else 0f)
        keySpace.pressAnim.animateTo(if (opts.keyJump.isDown) 1f else 0f)
    }

    private fun renderKeyCap(
        kx: Float, ky: Float, kw: Float, kh: Float,
        label: String, subLabel: String? = null,
        anim: KeyCapAnim,
        isSpace: Boolean = false
    ) {
        val factor = anim.pressAnim.value.coerceIn(0f, 1f)
        val radius = 4.0f

        val scale = 1f - factor * 0.05f
        val cx = kx + kw * 0.5f
        val cy = ky + kh * 0.5f

        val transform = NanoVGTransform(Draw2D.context)
        transform.withTransform {
            transform.translate(cx, cy)
            transform.scale(scale)
            transform.translate(-cx, -cy)

            val bgAlpha = HUDModule.bgOpacity.getFloat()
            drawWidgetShadow(kx, ky, kw, kh, radius, 0.45f, blur = 6f)

            val baseBg = NanoVGColor(0f, 0f, 0f, bgAlpha)
            val pressedBg = NanoVGColor(1f, 1f, 1f, 0.28f)
            val currentBg = baseBg.lerp(pressedBg, factor)
            drawBackdrop(kx, ky, kw, kh, radius, currentBg)

            val outlineAlpha = 0.08f + 0.32f * factor
            NanoVGRenderer.drawRoundedOutline(
                kx, ky, kw, kh, radius,
                NanoVGColor(1f, 1f, 1f, outlineAlpha),
                0.75f
            )

            val textShadow = HUDModule.textShadow.value
            val font = RenderEngine.fontRenderer
            if (isSpace) {
                val barW = kw * 0.42f
                val barH = 2.0f
                val barColor = if (factor > 0.3f) NanoVGColor.WHITE else NanoVGColor(0.85f, 0.88f, 0.95f, 0.90f)
                NanoVGRenderer.drawRoundedRect(
                    kx + (kw - barW) * 0.5f,
                    ky + (kh - barH) * 0.5f,
                    barW, barH, 1.0f, barColor
                )
            } else if (subLabel == null) {
                val textColor = if (factor > 0.3f) NanoVGColor.WHITE else NanoVGColor(0.92f, 0.94f, 0.98f, 0.95f)
                font.drawCenteredString(label, cx, cy, textColor, 11.0f, shadow = textShadow)
            } else {
                val textColor = if (factor > 0.3f) NanoVGColor.WHITE else NanoVGColor(0.92f, 0.94f, 0.98f, 0.95f)
                val subColor = NanoVGColor(0.65f, 0.68f, 0.76f, 0.90f)
                font.drawCenteredString(label, cx, ky + kh * 0.38f, textColor, 10.0f, shadow = textShadow)
                font.drawCenteredString(subLabel, cx, ky + kh * 0.70f, subColor, 8.0f, shadow = textShadow)
            }
        }
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return

        val capSize = 23.5f
        val gap = 3.5f
        width = capSize * 3f + gap * 2f

        var totalH = capSize * 2f + gap
        if (showMouse.value) totalH += 23f + gap
        if (showSpace.value) totalH += 12f + gap
        height = totalH

        val wX = x + capSize + gap
        renderKeyCap(wX, y, capSize, capSize, "W", null, keyW)

        val row2Y = y + capSize + gap
        renderKeyCap(x, row2Y, capSize, capSize, "A", null, keyA)
        renderKeyCap(x + capSize + gap, row2Y, capSize, capSize, "S", null, keyS)
        renderKeyCap(x + (capSize + gap) * 2f, row2Y, capSize, capSize, "D", null, keyD)

        var curY = row2Y + capSize + gap

        if (showMouse.value) {
            val mouseW = (width - gap) * 0.5f
            val mouseH = 23f
            val lmbCps = if (showCPS.value) "${keyLmb.getCps()} CPS" else null
            val rmbCps = if (showCPS.value) "${keyRmb.getCps()} CPS" else null

            renderKeyCap(x, curY, mouseW, mouseH, "LMB", lmbCps, keyLmb)
            renderKeyCap(x + mouseW + gap, curY, mouseW, mouseH, "RMB", rmbCps, keyRmb)
            curY += mouseH + gap
        }

        if (showSpace.value) {
            val spaceH = 12f
            renderKeyCap(x, curY, width, spaceH, "", null, keySpace, isSpace = true)
        }

        if (isEditing) {
            renderEditBoundingBox(partialTicks)
        }
    }
}
