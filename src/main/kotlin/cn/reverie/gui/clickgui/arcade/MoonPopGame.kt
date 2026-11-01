package cn.reverie.gui.clickgui.arcade

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Moon Pop: Fast-paced CPS & Combo clicker centered on the Nocturne Moon.
 */
class MoonPopGame : ArcadeGame {
    override val id = "pop"
    override val name = "Moon Pop"
    override val tabLabel = "Moon Pop"
    override val tabIcon = "POP"

    companion object {
        const val CANVAS_W = 560f
        const val CANVAS_H = 332f
    }

    private data class Ripple(
        var radius: Float,
        val maxRadius: Float,
        var alpha: Float,
        val color: NanoVGColor
    )

    private data class FloatingText(
        val text: String,
        var x: Float,
        var y: Float,
        var alpha: Float,
        val color: NanoVGColor
    )

    private data class PopParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val color: NanoVGColor,
        val size: Float
    )

    private var totalTaps = 0
    private var combo = 0
    private var maxCombo = 0
    private var maxCps = 0f
    private var lastClickTime = 0L

    private var scaleX = 1.0f
    private var scaleY = 1.0f
    private var shakeAmount = 0f

    private val clickTimestamps = mutableListOf<Long>()
    private val ripples = CopyOnWriteArrayList<Ripple>()
    private val floatTexts = CopyOnWriteArrayList<FloatingText>()
    private val particles = CopyOnWriteArrayList<PopParticle>()

    override fun init() {
        reset()
    }

    override fun reset() {
        totalTaps = 0
        combo = 0
        maxCombo = 0
        maxCps = 0f
        lastClickTime = 0L
        scaleX = 1.0f
        scaleY = 1.0f
        shakeAmount = 0f
        clickTimestamps.clear()
        ripples.clear()
        floatTexts.clear()
        particles.clear()
    }

    private fun getCurrentCps(): Float {
        val now = System.currentTimeMillis()
        clickTimestamps.removeAll { now - it > 1000L }
        return clickTimestamps.size.toFloat()
    }

    override fun update() {
        val now = System.currentTimeMillis()

        // Clean old CPS clicks
        clickTimestamps.removeAll { now - it > 1000L }
        val curCps = clickTimestamps.size.toFloat()
        if (curCps > maxCps) maxCps = curCps

        // Combo timeout (1.4s)
        if (combo > 0 && now - lastClickTime > 1400L) {
            combo = 0
        }

        // Spring return for moon squash
        scaleX += (1.0f - scaleX) * 0.18f
        scaleY += (1.0f - scaleY) * 0.18f
        shakeAmount *= 0.85f

        // Update ripples
        val rIter = ripples.iterator()
        while (rIter.hasNext()) {
            val r = rIter.next()
            r.radius += (r.maxRadius - r.radius) * 0.15f + 1.2f
            r.alpha -= 0.035f
            if (r.alpha <= 0f || r.radius >= r.maxRadius) {
                ripples.remove(r)
            }
        }

        // Update floating texts
        val tIter = floatTexts.iterator()
        while (tIter.hasNext()) {
            val t = tIter.next()
            t.y -= 1.4f
            t.alpha -= 0.032f
            if (t.alpha <= 0f) {
                floatTexts.remove(t)
            }
        }

        // Update particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx
            p.y += p.vy
            p.life -= 0.040f
            if (p.life <= 0f) {
                particles.remove(p)
            }
        }
    }

    private fun clickMoon(targetX: Float, targetY: Float, theme: NanoVGColor) {
        val now = System.currentTimeMillis()
        totalTaps++
        clickTimestamps.add(now)

        val prevPhase = getPhase(combo)
        combo++
        if (combo > maxCombo) maxCombo = combo
        lastClickTime = now
        val nextPhase = getPhase(combo)

        // Squash deformation
        scaleX = 1.26f
        scaleY = 0.74f

        if (combo >= 150) {
            shakeAmount = 4.0f
        }

        // Phase evolution sound or pop
        if (nextPhase > prevPhase) {
            ArcadeSoundHelper.playUnlock()
        } else {
            val pitch = (1.0f + (combo.coerceIn(0, 100) / 100f * 0.9f))
            ArcadeSoundHelper.playPop(pitch)
        }

        // Add ripple
        val rippleColor = when (nextPhase) {
            4 -> NanoVGColor(0.95f, 0.35f, 0.75f, 0.70f)
            3 -> NanoVGColor(1.0f, 0.85f, 0.30f, 0.70f)
            2 -> NanoVGColor(0.35f, 0.85f, 1.0f, 0.70f)
            else -> theme.withAlpha(0.65f)
        }
        ripples.add(Ripple(radius = 35f, maxRadius = 110f, alpha = 0.85f, color = rippleColor))

        // Add floating text
        val comboBonus = if (combo % 10 == 0) "COMBO $combo!" else "+1"
        floatTexts.add(
            FloatingText(
                text = comboBonus,
                x = targetX + (Random.nextFloat() - 0.5f) * 35f,
                y = targetY - 25f,
                alpha = 1.0f,
                color = rippleColor
            )
        )

        // Burst particles
        repeat(5) {
            particles.add(
                PopParticle(
                    x = targetX,
                    y = targetY,
                    vx = (Random.nextFloat() - 0.5f) * 6f,
                    vy = (Random.nextFloat() - 0.5f) * 6f,
                    life = 1.0f,
                    color = rippleColor,
                    size = 2.5f + Random.nextFloat() * 2.0f
                )
            )
        }
    }

    private fun getPhase(c: Int): Int = when {
        c >= 150 -> 4
        c >= 80 -> 3
        c >= 30 -> 2
        else -> 1
    }

    override fun render(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mouseX: Float,
        mouseY: Float,
        alpha: Float,
        theme: NanoVGColor
    ) {
        val now = System.currentTimeMillis()
        val curCps = getCurrentCps()
        val phase = getPhase(combo)

        NanoVGRenderer.withScissor(x, y, width, height) {
            val nvg = RenderEngine.nanoVG
            nvg.transform.save()
            nvg.transform.translate(x, y)

            // Background
            Draw2D.rect(0f, 0f, width, height, NanoVGColor(0.04f, 0.04f, 0.07f, 0.75f * alpha))

            // Center coords
            val cx = width * 0.5f + (sin(now / 30.0).toFloat() * shakeAmount)
            val cy = height * 0.50f + (cos(now / 30.0).toFloat() * shakeAmount)

            // Orbiting Stardust Particles around Moon
            val orbitRadius = 72f
            val orbitCount = if (phase >= 4) 8 else if (phase >= 3) 6 else if (phase >= 2) 4 else 3
            val orbitSpeed = if (phase >= 4) 0.007 else if (phase >= 3) 0.005 else 0.003
            for (i in 0 until orbitCount) {
                val angle = now * orbitSpeed + (i * Math.PI * 2.0 / orbitCount)
                val ox = cx + cos(angle).toFloat() * orbitRadius
                val oy = cy + sin(angle).toFloat() * (orbitRadius * 0.45f)
                val pColor = when (phase) {
                    4 -> NanoVGColor(1.0f, 0.4f, 0.85f, 0.85f * alpha)
                    3 -> NanoVGColor(1.0f, 0.9f, 0.35f, 0.85f * alpha)
                    2 -> NanoVGColor(0.4f, 0.9f, 1.0f, 0.85f * alpha)
                    else -> NanoVGColor(0.85f, 0.90f, 1.0f, 0.65f * alpha)
                }
                Draw2D.circle(ox, oy, 2.2f, pColor)
            }

            // Draw expanding ripples
            for (r in ripples) {
                Draw2D.circleOutline(cx, cy, r.radius, r.color.withAlpha(r.alpha * alpha), 1.5f)
            }

            // Draw burst particles
            for (p in particles) {
                Draw2D.circle(p.x, p.y, p.size, p.color.withAlpha(p.life * alpha))
            }

            // Center Moon with squash & stretch transform
            nvg.transform.save()
            nvg.transform.translate(cx, cy)
            nvg.transform.scale(scaleX, scaleY)

            // Moon Aura Halo
            val haloRadius = 45f
            val haloColor = when (phase) {
                4 -> NanoVGColor(0.95f, 0.25f, 0.65f, 0.35f * alpha)
                3 -> NanoVGColor(1.0f, 0.85f, 0.25f, 0.35f * alpha)
                2 -> NanoVGColor(0.35f, 0.85f, 1.0f, 0.30f * alpha)
                else -> theme.withAlpha(0.22f * alpha)
            }
            Draw2D.circle(0f, 0f, haloRadius, haloColor)
            Draw2D.circleOutline(0f, 0f, haloRadius, haloColor.withAlpha(0.60f * alpha), 1.2f)

            // Draw Main Moon
            val moonSize = 58f
            val moonColor = when (phase) {
                4 -> NanoVGColor(1.0f, 0.45f, 0.80f, 0.98f * alpha)
                3 -> NanoVGColor(1.0f, 0.92f, 0.45f, 0.98f * alpha)
                2 -> NanoVGColor(0.65f, 0.90f, 1.0f, 0.98f * alpha)
                else -> NanoVGColor(0.94f, 0.96f, 1.0f, 0.95f * alpha)
            }
            HudVectorIcons.drawMoon(-moonSize * 0.5f, -moonSize * 0.5f, moonSize, moonColor)

            nvg.transform.restore()

            // Draw Floating Combo Texts
            for (t in floatTexts) {
                NanoVGRenderer.drawCenteredString(t.text, t.x, t.y, t.color.withAlpha(t.alpha * alpha), 13f)
            }

            // Header Stats Bar
            val cpsText = String.format("%.1f CPS", curCps)
            NanoVGRenderer.drawString(cpsText, 18f, 14f, theme.withAlpha(0.95f * alpha), 14f)
            NanoVGRenderer.drawString("PEAK ${String.format("%.1f", maxCps)}", 18f, 30f, NanoVGColor(0.55f, 0.60f, 0.70f, 0.75f * alpha), 9f)

            val totalText = "TOTAL $totalTaps"
            val totalW = NanoVGRenderer.getTextWidth(totalText, 11f)
            NanoVGRenderer.drawString(totalText, width - totalW - 18f, 14f, NanoVGColor(0.85f, 0.88f, 0.95f, 0.85f * alpha), 11f)

            val maxComboText = "MAX COMBO $maxCombo"
            val maxCW = NanoVGRenderer.getTextWidth(maxComboText, 9f)
            NanoVGRenderer.drawString(maxComboText, width - maxCW - 18f, 29f, NanoVGColor(0.55f, 0.60f, 0.70f, 0.75f * alpha), 9f)

            // Phase Title & Badge below Moon
            val (phaseName, phaseBadge) = when (phase) {
                4 -> Pair("ECLIPSE GOD", "PHASE IV · 蚀月狂暴")
                3 -> Pair("FULL MOON", "PHASE III · 璀璨满月")
                2 -> Pair("GIBBOUS", "PHASE II · 极光弦月")
                else -> Pair("CRESCENT", "PHASE I · 幽夜新月")
            }

            val titleW = NanoVGRenderer.getTextWidth(phaseName, 12f)
            NanoVGRenderer.drawString(phaseName, cx - titleW * 0.5f, cy + 46f, haloColor.withAlpha(0.95f * alpha), 12f)

            val badgeW = NanoVGRenderer.getTextWidth(phaseBadge, 9f)
            NanoVGRenderer.drawString(phaseBadge, cx - badgeW * 0.5f, cy + 62f, NanoVGColor(0.60f, 0.65f, 0.75f, 0.70f * alpha), 9f)

            // Combo Bar & Timer at Bottom
            if (combo > 0) {
                val timePassed = (now - lastClickTime).coerceIn(0L, 1400L)
                val comboProgress = 1.0f - (timePassed / 1400f)

                val barW = 160f
                val barH = 4f
                val barX = cx - barW * 0.5f
                val barY = cy + 78f

                Draw2D.roundedRect(barX, barY, barW, barH, 2f, NanoVGColor(1f, 1f, 1f, 0.10f * alpha))
                if (comboProgress > 0.01f) {
                    Draw2D.roundedRect(barX, barY, barW * comboProgress, barH, 2f, haloColor.withAlpha(0.85f * alpha))
                }

                val comboStr = "$combo COMBO"
                val comboW = NanoVGRenderer.getTextWidth(comboStr, 10f)
                NanoVGRenderer.drawString(comboStr, cx - comboW * 0.5f, barY - 12f, NanoVGColor.WHITE.withAlpha(alpha), 10f)
            } else {
                val tapHint = "CLICK RAPIDLY TO AWAKEN THE MOON"
                val hintW = NanoVGRenderer.getTextWidth(tapHint, 9.5f)
                val pulse = (0.6f + sin(now / 200.0).toFloat() * 0.35f).coerceIn(0.2f, 1.0f)
                NanoVGRenderer.drawString(tapHint, cx - hintW * 0.5f, cy + 74f, NanoVGColor(0.65f, 0.70f, 0.80f, pulse * alpha), 9.5f)
            }

            nvg.transform.restore()
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button == 0) {
            clickMoon(mouseX, mouseY, NanoVGColor(0.4f, 0.75f, 1.0f, 1.0f))
            return true
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            clickMoon(CANVAS_W * 0.5f, CANVAS_H * 0.50f, NanoVGColor(0.4f, 0.75f, 1.0f, 1.0f))
            return true
        }
        return false
    }
}
