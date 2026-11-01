package cn.reverie.render.hud

import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import net.minecraft.client.Minecraft
import kotlin.math.hypot

class CrosshairRenderer {

    var isEnabled = true
    var size: Float = 4.5f
    var baseGap: Float = 3.0f
    var thickness: Float = 1.4f
    var showDot: Boolean = true

    private val spreadAnim = FloatAnimation(0f, 0f, 120L, CubicBezier.EASE_OUT)

    fun onTick() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        val isMoving = player != null && (hypot(player.x - player.xo, player.z - player.zo) > 0.05)
        val isJumping = player != null && !player.onGround()
        val attackCooldown = player?.getAttackStrengthScale(0f) ?: 1.0f

        val targetSpread = when {
            attackCooldown < 0.95f -> 4.5f
            isJumping -> 3.5f
            isMoving -> 2.0f
            else -> 0f
        }
        spreadAnim.animateTo(targetSpread)
    }

    fun render() {
        if (!isEnabled || !HUDModule.showCrosshair.value) return

        val cx = ResolutionHelper.scaledWidth * 0.5f
        val cy = ResolutionHelper.scaledHeight * 0.5f

        val color = HUDModule.sampleColor(0f).multiplyAlpha(0.92f)

        val dynamicGap = baseGap + spreadAnim.value

        if (showDot) {
            Draw2D.circle(cx, cy, 1.2f, color)
        }

        Draw2D.line(cx, cy - dynamicGap - size, cx, cy - dynamicGap, color, thickness)
        Draw2D.line(cx, cy + dynamicGap, cx, cy + dynamicGap + size, color, thickness)
        Draw2D.line(cx - dynamicGap - size, cy, cx - dynamicGap, cy, color, thickness)
        Draw2D.line(cx + dynamicGap, cy, cx + dynamicGap + size, cy, color, thickness)
    }
}
