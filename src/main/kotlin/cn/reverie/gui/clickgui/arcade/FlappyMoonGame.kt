package cn.reverie.gui.clickgui.arcade

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.sin
import kotlin.random.Random

/**
 * Flappy Moon: Control the Nocturne Moon navigating through celestial pillars.
 */
class FlappyMoonGame : ArcadeGame {
    override val id = "flappy"
    override val name = "Flappy Moon"
    override val tabLabel = "Flappy Moon"
    override val tabIcon = "MOON"

    enum class State {
        READY,
        PLAYING,
        GAME_OVER
    }

    private data class Pillar(
        var x: Float,
        val topHeight: Float,
        val gap: Float = 90f,
        var passed: Boolean = false
    )

    private data class Star(
        var x: Float,
        var y: Float,
        var collected: Boolean = false
    )

    private data class Powerup(
        var x: Float,
        var y: Float,
        var collected: Boolean = false
    )

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val maxLife: Float,
        val color: NanoVGColor,
        val size: Float
    )

    private var state = State.READY
    private var moonY = 150f
    private var velocity = 0f
    private var rotation = 0f
    private var score = 0
    private var invincibleUntil = 0L
    private var lastPillarSpawn = 0L
    private var gameOverAt = 0L

    private val pillars = CopyOnWriteArrayList<Pillar>()
    private val stars = CopyOnWriteArrayList<Star>()
    private val powerups = CopyOnWriteArrayList<Powerup>()
    private val particles = CopyOnWriteArrayList<Particle>()

    companion object {
        var highScore: Int = 0
        const val CANVAS_W = 560f
        const val CANVAS_H = 332f
        const val MOON_X = 85f
        const val MOON_RADIUS = 10f
        const val PILLAR_W = 36f
        const val PILLAR_GAP = 90f
    }

    override fun init() {
        reset()
    }

    override fun reset() {
        state = State.READY
        moonY = 150f
        velocity = 0f
        rotation = 0f
        score = 0
        invincibleUntil = 0L
        pillars.clear()
        stars.clear()
        powerups.clear()
        particles.clear()
    }

    private fun jump() {
        velocity = -4.6f
        rotation = -28f
        ArcadeSoundHelper.playJump()
    }

    override fun update() {
        val now = System.currentTimeMillis()

        // Update trail particles
        val pIter = particles.iterator()
        while (pIter.hasNext()) {
            val p = pIter.next()
            p.x += p.vx
            p.y += p.vy
            p.life -= 0.045f
            if (p.life <= 0f) {
                particles.remove(p)
            }
        }

        when (state) {
            State.READY -> {
                velocity = 0f
                moonY = 150f + sin(now / 220.0).toFloat() * 7f
                rotation = sin(now / 350.0).toFloat() * 8f
            }
            State.PLAYING -> {
                // Gravity & Movement
                velocity += 0.25f
                if (velocity > 6.8f) velocity = 6.8f
                moonY += velocity

                val targetRot = if (velocity < 0) -24f else (velocity * 6.5f).coerceAtMost(55f)
                rotation += (targetRot - rotation) * 0.16f

                val isInvincible = now < invincibleUntil

                // Moon trail
                if (Random.nextFloat() < 0.65f) {
                    val trailColor = if (isInvincible) NanoVGColor(1.0f, 0.88f, 0.40f, 0.70f) else NanoVGColor(0.70f, 0.85f, 1.0f, 0.50f)
                    particles.add(
                        Particle(
                            x = MOON_X - 10f,
                            y = moonY + (Random.nextFloat() - 0.5f) * 6f,
                            vx = -1.5f - Random.nextFloat() * 1.2f,
                            vy = (Random.nextFloat() - 0.5f) * 1.0f,
                            life = 1.0f,
                            maxLife = 1.0f,
                            color = trailColor,
                            size = 2.0f + Random.nextFloat() * 2.5f
                        )
                    )
                }

                // Spawn pillars
                if (now - lastPillarSpawn > 1750L) {
                    lastPillarSpawn = now
                    val topH = 35f + Random.nextFloat() * (CANVAS_H - PILLAR_GAP - 80f)
                    val p = Pillar(x = CANVAS_W + 10f, topHeight = topH, gap = PILLAR_GAP)
                    pillars.add(p)

                    // Chance to spawn star or powerup
                    if (Random.nextFloat() < 0.65f) {
                        stars.add(Star(x = CANVAS_W + 10f + PILLAR_W * 0.5f, y = topH + PILLAR_GAP * 0.5f))
                    } else if (Random.nextFloat() < 0.24f) {
                        powerups.add(Powerup(x = CANVAS_W + 10f + PILLAR_W * 0.5f, y = topH + PILLAR_GAP * 0.5f))
                    }
                }

                // Top / bottom boundary check
                if (moonY - MOON_RADIUS < 2f) {
                    moonY = 2f + MOON_RADIUS
                    velocity = 0.5f
                }
                if (moonY + MOON_RADIUS > CANVAS_H - 4f) {
                    triggerGameOver()
                    return
                }

                // Move pillars & check collisions
                val speed = if (isInvincible) 3.2f else 2.3f
                for (p in pillars) {
                    p.x -= speed

                    // Score when passing
                    if (!p.passed && p.x + PILLAR_W < MOON_X) {
                        p.passed = true
                        score++
                        if (score > highScore) highScore = score
                        ArcadeSoundHelper.playScore()
                    }

                    // Collision check
                    if (!isInvincible && p.x < MOON_X + MOON_RADIUS && p.x + PILLAR_W > MOON_X - MOON_RADIUS) {
                        val bottomY = p.topHeight + p.gap
                        if (moonY - MOON_RADIUS < p.topHeight || moonY + MOON_RADIUS > bottomY) {
                            triggerGameOver()
                            return
                        }
                    }
                }
                pillars.removeIf { it.x < -50f }

                // Move stars & collect
                for (star in stars) {
                    star.x -= speed
                    if (!star.collected && (star.x - MOON_X) * (star.x - MOON_X) + (star.y - moonY) * (star.y - moonY) < 22f * 22f) {
                        star.collected = true
                        score += 3
                        if (score > highScore) highScore = score
                        ArcadeSoundHelper.playPowerup()
                        // Burst particles
                        repeat(6) {
                            particles.add(
                                Particle(
                                    x = star.x,
                                    y = star.y,
                                    vx = (Random.nextFloat() - 0.5f) * 4f,
                                    vy = (Random.nextFloat() - 0.5f) * 4f,
                                    life = 1f,
                                    maxLife = 1f,
                                    color = NanoVGColor(1.0f, 0.90f, 0.3f, 0.9f),
                                    size = 3f
                                )
                            )
                        }
                    }
                }
                stars.removeIf { it.x < -30f || it.collected }

                // Move powerups & collect
                for (pw in powerups) {
                    pw.x -= speed
                    if (!pw.collected && (pw.x - MOON_X) * (pw.x - MOON_X) + (pw.y - moonY) * (pw.y - moonY) < 24f * 24f) {
                        pw.collected = true
                        invincibleUntil = now + 4000L
                        ArcadeSoundHelper.playUnlock()
                        repeat(12) {
                            particles.add(
                                Particle(
                                    x = pw.x,
                                    y = pw.y,
                                    vx = (Random.nextFloat() - 0.5f) * 5f,
                                    vy = (Random.nextFloat() - 0.5f) * 5f,
                                    life = 1f,
                                    maxLife = 1f,
                                    color = NanoVGColor(0.4f, 0.85f, 1.0f, 0.9f),
                                    size = 4f
                                )
                            )
                        }
                    }
                }
                powerups.removeIf { it.x < -30f || it.collected }
            }
            State.GAME_OVER -> {
                if (moonY < CANVAS_H - 6f) {
                    velocity += 0.35f
                    moonY += velocity
                    rotation += 4f
                }
            }
        }
    }

    private fun triggerGameOver() {
        state = State.GAME_OVER
        gameOverAt = System.currentTimeMillis()
        ArcadeSoundHelper.playCrash()
        repeat(16) {
            particles.add(
                Particle(
                    x = MOON_X,
                    y = moonY,
                    vx = (Random.nextFloat() - 0.5f) * 6f,
                    vy = (Random.nextFloat() - 0.5f) * 6f,
                    life = 1f,
                    maxLife = 1f,
                    color = NanoVGColor(1.0f, 0.4f, 0.4f, 0.85f),
                    size = 3.5f
                )
            )
        }
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
        val isInvincible = now < invincibleUntil
        val localMx = mouseX - x
        val localMy = mouseY - y

        NanoVGRenderer.withScissor(x, y, width, height) {
            val nvg = RenderEngine.nanoVG
            nvg.transform.save()
            nvg.transform.translate(x, y)

            // Background starry night canvas
            Draw2D.rect(0f, 0f, width, height, NanoVGColor(0.04f, 0.04f, 0.07f, 0.75f * alpha))

            // Subtle starlight background dots
            for (i in 0 until 18) {
                val sx = ((i * 37f + (now * 0.015f * (i % 3 + 1))) % width)
                val sy = ((i * 29f + (i * 13f)) % height)
                val starAlpha = (0.20f + sin(now / 300.0 + i).toFloat() * 0.15f).coerceIn(0.05f, 0.40f)
                Draw2D.circle(sx, sy, 1.2f, NanoVGColor(1f, 1f, 1f, starAlpha * alpha))
            }

            // Draw Pillars
            for (p in pillars) {
                val px = p.x
                if (px + PILLAR_W < 0f || px > width) continue

                // Top pillar
                if (p.topHeight > 0f) {
                    Draw2D.roundedRect(px, 0f, PILLAR_W, p.topHeight, 4f, NanoVGColor(0.12f, 0.14f, 0.20f, 0.88f * alpha))
                    Draw2D.roundedOutline(px, -2f, PILLAR_W, p.topHeight + 2f, 4f, NanoVGColor(0.35f, 0.45f, 0.65f, 0.45f * alpha), 1f)
                    Draw2D.roundedRect(px - 1.5f, p.topHeight - 6f, PILLAR_W + 3f, 6f, 3f, theme.withAlpha(0.65f * alpha))
                }

                // Bottom pillar
                val botY = p.topHeight + p.gap
                val botH = height - botY
                if (botH > 0f) {
                    Draw2D.roundedRect(px, botY, PILLAR_W, botH, 4f, NanoVGColor(0.12f, 0.14f, 0.20f, 0.88f * alpha))
                    Draw2D.roundedOutline(px, botY, PILLAR_W, botH + 2f, 4f, NanoVGColor(0.35f, 0.45f, 0.65f, 0.45f * alpha), 1f)
                    Draw2D.roundedRect(px - 1.5f, botY, PILLAR_W + 3f, 6f, 3f, theme.withAlpha(0.65f * alpha))
                }
            }

            // Draw Stars
            for (star in stars) {
                val sx = star.x
                val sy = star.y
                val bob = sin(now / 150.0).toFloat() * 2f
                val starGlow = NanoVGColor(1f, 0.88f, 0.35f, 0.85f * alpha)
                HudVectorIcons.draw(HudVectorIcons.SPARKLES, sx - 7f, sy - 7f + bob, 14f, starGlow, strokeWidth = 1.0f)
                Draw2D.circle(sx, sy + bob, 2.5f, starGlow)
            }

            // Draw Powerups (Full Moon Orb)
            for (pw in powerups) {
                val px = pw.x
                val py = pw.y
                val bob = sin(now / 180.0).toFloat() * 3f
                Draw2D.circle(px, py + bob, 8.5f, NanoVGColor(0.3f, 0.75f, 1.0f, 0.35f * alpha))
                Draw2D.circleOutline(px, py + bob, 9.5f, NanoVGColor(0.5f, 0.85f, 1.0f, 0.80f * alpha), 1.2f)
                HudVectorIcons.drawMoon(px - 6f, py - 6f + bob, 12f, NanoVGColor(1f, 1f, 1f, 0.95f * alpha))
            }

            // Draw particles
            for (p in particles) {
                Draw2D.circle(p.x, p.y, p.size, p.color.withAlpha(p.life * alpha))
            }

            // Draw the Player: Nocturne Moon
            nvg.transform.save()
            nvg.transform.translate(MOON_X, moonY)
            nvg.transform.rotate(Math.toRadians(rotation.toDouble()).toFloat())

            // Moon Outer Glow Aura
            val glowColor = if (isInvincible) {
                NanoVGColor(1.0f, 0.85f, 0.30f, 0.40f * alpha)
            } else {
                theme.withAlpha(0.28f * alpha)
            }
            Draw2D.circle(0f, 0f, 15f, glowColor)

            // Draw the Moon Icon
            val moonColor = if (isInvincible) {
                NanoVGColor(1.0f, 0.95f, 0.60f, 0.98f * alpha)
            } else {
                NanoVGColor(0.92f, 0.95f, 1.0f, 0.95f * alpha)
            }
            HudVectorIcons.drawMoon(-11f, -11f, 22f, moonColor)

            nvg.transform.restore()

            // HUD Overlay (Score & Status)
            if (state == State.PLAYING || state == State.GAME_OVER) {
                val scoreStr = "$score"
                val scoreW = NanoVGRenderer.getTextWidth(scoreStr, 18f)
                NanoVGRenderer.drawString(scoreStr, (width - scoreW) * 0.5f, 14f, NanoVGColor.WHITE.withAlpha(alpha), 18f)

                val bestStr = "BEST $highScore"
                NanoVGRenderer.drawString(bestStr, 16f, 14f, NanoVGColor(0.65f, 0.70f, 0.80f, 0.80f * alpha), 10f)

                if (isInvincible) {
                    val remainMs = (invincibleUntil - now).coerceAtLeast(0L)
                    val buffStr = "LUNAR RUSH ${(remainMs / 100) / 10f}s"
                    NanoVGRenderer.drawString(buffStr, width - 110f, 14f, NanoVGColor(1.0f, 0.88f, 0.35f, 0.95f * alpha), 10.5f)
                }
            }

            // State Screens
            when (state) {
                State.READY -> {
                    val prompt = "PRESS SPACE OR CLICK TO JUMP"
                    val promptW = NanoVGRenderer.getTextWidth(prompt, 11.5f)
                    val pulse = (0.70f + sin(now / 180.0).toFloat() * 0.30f).coerceIn(0.3f, 1.0f)
                    NanoVGRenderer.drawString(
                        prompt,
                        (width - promptW) * 0.5f,
                        height * 0.68f,
                        theme.withAlpha(pulse * alpha),
                        11.5f
                    )

                    val hint = "Collect ★ (+3) & 🌕 (Lunar Rush)"
                    val hintW = NanoVGRenderer.getTextWidth(hint, 9.5f)
                    NanoVGRenderer.drawString(
                        hint,
                        (width - hintW) * 0.5f,
                        height * 0.78f,
                        NanoVGColor(0.60f, 0.65f, 0.75f, 0.75f * alpha),
                        9.5f
                    )
                }
                State.GAME_OVER -> {
                    val cardW = 210f
                    val cardH = 104f
                    val cardX = (width - cardW) * 0.5f
                    val cardY = (height - cardH) * 0.5f

                    Draw2D.roundedRect(cardX, cardY, cardW, cardH, 8f, NanoVGColor(0.06f, 0.07f, 0.11f, 0.92f * alpha))
                    Draw2D.roundedOutline(cardX, cardY, cardW, cardH, 8f, NanoVGColor(1f, 1f, 1f, 0.15f * alpha), 1f)

                    val overText = "GAME OVER"
                    val overW = NanoVGRenderer.getTextWidth(overText, 14f)
                    NanoVGRenderer.drawString(overText, cardX + (cardW - overW) * 0.5f, cardY + 16f, NanoVGColor(1.0f, 0.4f, 0.45f, alpha), 14f)

                    val resultText = "Score: $score   Best: $highScore"
                    val resW = NanoVGRenderer.getTextWidth(resultText, 11f)
                    NanoVGRenderer.drawString(resultText, cardX + (cardW - resW) * 0.5f, cardY + 42f, NanoVGColor(0.85f, 0.88f, 0.95f, alpha), 11f)

                    // Retry button
                    val btnW = 104f
                    val btnH = 22f
                    val btnX = cardX + (cardW - btnW) * 0.5f
                    val btnY = cardY + cardH - 32f
                    val isHoverRetry = localMx in btnX..(btnX + btnW) && localMy in btnY..(btnY + btnH)

                    val btnBg = if (isHoverRetry) theme.withAlpha(0.40f * alpha) else NanoVGColor(1f, 1f, 1f, 0.08f * alpha)
                    Draw2D.roundedRect(btnX, btnY, btnW, btnH, 5f, btnBg)
                    Draw2D.roundedOutline(btnX, btnY, btnW, btnH, 5f, if (isHoverRetry) theme else NanoVGColor(1f, 1f, 1f, 0.15f * alpha), 1f)

                    val retryStr = "PLAY AGAIN"
                    val rW = NanoVGRenderer.getTextWidth(retryStr, 9.5f)
                    NanoVGRenderer.drawString(retryStr, btnX + (btnW - rW) * 0.5f, btnY + 6.5f, NanoVGColor.WHITE.withAlpha(alpha), 9.5f)
                }
                else -> {}
            }

            nvg.transform.restore()
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != 0) return false
        when (state) {
            State.READY -> {
                state = State.PLAYING
                jump()
                return true
            }
            State.PLAYING -> {
                jump()
                return true
            }
            State.GAME_OVER -> {
                if (System.currentTimeMillis() - gameOverAt > 300L) {
                    reset()
                    state = State.PLAYING
                    jump()
                    return true
                }
            }
        }
        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_W) {
            when (state) {
                State.READY -> {
                    state = State.PLAYING
                    jump()
                    return true
                }
                State.PLAYING -> {
                    jump()
                    return true
                }
                State.GAME_OVER -> {
                    if (System.currentTimeMillis() - gameOverAt > 300L) {
                        reset()
                        state = State.PLAYING
                        jump()
                        return true
                    }
                }
            }
        }
        return false
    }
}
