package cn.reverie.gui.clickgui.arcade

import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudVectorIcons
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.glfw.GLFW
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.pow
import kotlin.math.sin

/**
 * Moon Piano: Nocturne melodic synthesizer and sheet music challenge.
 */
class MoonPianoGame : ArcadeGame {
    override val id = "piano"
    override val name = "Moon Piano"
    override val tabLabel = "Moon Piano"
    override val tabIcon = "PIANO"

    companion object {
        const val CANVAS_W = 560f
        const val CANVAS_H = 332f
        const val START_PIANO_X = 20f
        const val PIANO_Y = 96f
        const val TOTAL_KEYS_W = CANVAS_W - 40f // 520f
        const val WHITE_KEY_COUNT = 8
        const val WHITE_KEY_W = TOTAL_KEYS_W / WHITE_KEY_COUNT // 65f
        const val WHITE_KEY_H = CANVAS_H - PIANO_Y - 14f // 222f
        const val BLACK_KEY_W = WHITE_KEY_W * 0.58f // 37.7f
        const val BLACK_KEY_H = WHITE_KEY_H * 0.58f // 128.76f
    }

    data class PianoKey(
        val name: String,
        val noteNum: String,
        val shortcut: String,
        val keyCode: Int,
        val semitone: Int,
        val isBlack: Boolean,
        var pressUntil: Long = 0L
    ) {
        val pitch: Float = 2.0.pow((semitone - 12) / 12.0).toFloat()
    }

    private data class SoundWave(
        var radius: Float,
        var alpha: Float,
        val color: NanoVGColor
    )

    private data class NoteConfetti(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val color: NanoVGColor
    )

    private val whiteKeys = listOf(
        PianoKey("C4", "1", "A / 1", GLFW.GLFW_KEY_A, 6, false),
        PianoKey("D4", "2", "S / 2", GLFW.GLFW_KEY_S, 8, false),
        PianoKey("E4", "3", "D / 3", GLFW.GLFW_KEY_D, 10, false),
        PianoKey("F4", "4", "F / 4", GLFW.GLFW_KEY_F, 11, false),
        PianoKey("G4", "5", "G / 5", GLFW.GLFW_KEY_G, 13, false),
        PianoKey("A4", "6", "H / 6", GLFW.GLFW_KEY_H, 15, false),
        PianoKey("B4", "7", "J / 7", GLFW.GLFW_KEY_J, 17, false),
        PianoKey("C5", "i", "K / 8", GLFW.GLFW_KEY_K, 18, false)
    )

    private val blackKeys = listOf(
        PianoKey("C#4", "1#", "W", GLFW.GLFW_KEY_W, 7, true),
        PianoKey("D#4", "2#", "E", GLFW.GLFW_KEY_E, 9, true),
        PianoKey("F#4", "4#", "T", GLFW.GLFW_KEY_T, 12, true),
        PianoKey("G#4", "5#", "Y", GLFW.GLFW_KEY_Y, 14, true),
        PianoKey("A#4", "6#", "U", GLFW.GLFW_KEY_U, 16, true)
    )

    private val blackKeyIndices = listOf(0, 1, 3, 4, 5)

    // Number keys mapped to white keys 1-8
    private val numberKeyMap = mapOf(
        GLFW.GLFW_KEY_1 to 0,
        GLFW.GLFW_KEY_2 to 1,
        GLFW.GLFW_KEY_3 to 2,
        GLFW.GLFW_KEY_4 to 3,
        GLFW.GLFW_KEY_5 to 4,
        GLFW.GLFW_KEY_6 to 5,
        GLFW.GLFW_KEY_7 to 6,
        GLFW.GLFW_KEY_8 to 7
    )

    // Nocturne Op.9 No.2 Melody Challenge sequence
    private val nocturneMelody = listOf("G4", "G4", "A4", "G4", "F4", "E4", "E4", "E4", "F4", "G4")
    private var songProgress = 0
    private var songCompletedAt = 0L

    private var moonPulse = 0f
    private val soundWaves = CopyOnWriteArrayList<SoundWave>()
    private val confettis = CopyOnWriteArrayList<NoteConfetti>()

    override fun init() {
        reset()
    }

    override fun reset() {
        songProgress = 0
        songCompletedAt = 0L
        moonPulse = 0f
        soundWaves.clear()
        confettis.clear()
    }

    private fun playKey(key: PianoKey) {
        val now = System.currentTimeMillis()
        key.pressUntil = now + 160L
        moonPulse = 1.0f

        ArcadeSoundHelper.playHarp(key.pitch)
        soundWaves.add(SoundWave(radius = 16f, alpha = 0.85f, color = NanoVGColor(0.5f, 0.85f, 1.0f, 0.7f)))

        // Check melody challenge
        if (songProgress < nocturneMelody.size) {
            val targetKeyName = nocturneMelody[songProgress]
            if (key.name == targetKeyName) {
                songProgress++
                if (songProgress >= nocturneMelody.size) {
                    songCompletedAt = now
                    ArcadeSoundHelper.playUnlock()
                    repeat(26) {
                        confettis.add(
                            NoteConfetti(
                                x = CANVAS_W * 0.5f,
                                y = 50f,
                                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 8f,
                                vy = (kotlin.random.Random.nextFloat() - 0.7f) * 6f,
                                life = 1.0f,
                                color = NanoVGColor(1.0f, 0.85f, 0.35f, 0.9f)
                            )
                        )
                    }
                }
            }
        }
    }

    override fun update() {
        moonPulse *= 0.90f

        val wIter = soundWaves.iterator()
        while (wIter.hasNext()) {
            val w = wIter.next()
            w.radius += 2.4f
            w.alpha -= 0.035f
            if (w.alpha <= 0f) soundWaves.remove(w)
        }

        val cIter = confettis.iterator()
        while (cIter.hasNext()) {
            val c = cIter.next()
            c.x += c.vx
            c.y += c.vy
            c.vy += 0.18f
            c.life -= 0.025f
            if (c.life <= 0f) confettis.remove(c)
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
        val localMx = mouseX - x
        val localMy = mouseY - y

        NanoVGRenderer.withScissor(x, y, width, height) {
            val nvg = RenderEngine.nanoVG
            nvg.transform.save()
            nvg.transform.translate(x, y)

            // Background
            Draw2D.rect(0f, 0f, width, height, NanoVGColor(0.04f, 0.04f, 0.07f, 0.75f * alpha))

            // Resonant Floating Moon at top
            val moonCenterX = width * 0.5f
            val moonCenterY = 40f

            // Sound waves radiating from moon
            for (w in soundWaves) {
                Draw2D.circleOutline(moonCenterX, moonCenterY, w.radius, w.color.withAlpha(w.alpha * alpha), 1.2f)
            }

            // Confetti
            for (c in confettis) {
                Draw2D.circle(c.x, c.y, 2.8f, c.color.withAlpha(c.life * alpha))
            }

            // Moon Sphere
            val baseRadius = 18f
            val glowRadius = baseRadius + 14f * moonPulse
            Draw2D.circle(moonCenterX, moonCenterY, glowRadius, theme.withAlpha((0.18f + 0.35f * moonPulse) * alpha))

            val moonColor = NanoVGColor.WHITE.lerp(NanoVGColor(0.6f, 0.85f, 1.0f, 1.0f), moonPulse)
            HudVectorIcons.drawMoon(moonCenterX - 14f, moonCenterY - 14f, 28f, moonColor.withAlpha(alpha))

            // Melody Challenge ribbon
            val sheetY = 74f
            val isSongDone = songProgress >= nocturneMelody.size
            if (isSongDone && now - songCompletedAt < 5000L) {
                val banner = "★ NOCTURNE OP.9 NO.2 COMPLETE! ★"
                val bW = NanoVGRenderer.getTextWidth(banner, 11.5f)
                NanoVGRenderer.drawString(banner, (width - bW) * 0.5f, sheetY, NanoVGColor(1.0f, 0.88f, 0.35f, alpha), 11.5f)
            } else {
                val prompt = "CHOPIN: NOCTURNE MELODY"
                val pW = NanoVGRenderer.getTextWidth(prompt, 9f)
                NanoVGRenderer.drawString(prompt, 20f, sheetY, NanoVGColor(0.60f, 0.65f, 0.75f, 0.70f * alpha), 9f)

                // Render note progression boxes
                val startBoxX = 160f
                val boxW = 20f
                val boxH = 14f
                for (i in nocturneMelody.indices) {
                    val noteName = nocturneMelody[i]
                    val bx = startBoxX + i * (boxW + 4f)
                    val isPassed = i < songProgress
                    val isCurrent = i == songProgress

                    val boxBg = when {
                        isPassed -> NanoVGColor(0.2f, 0.75f, 0.4f, 0.45f * alpha)
                        isCurrent -> theme.withAlpha((0.55f + sin(now / 150.0).toFloat() * 0.25f) * alpha)
                        else -> NanoVGColor(1f, 1f, 1f, 0.08f * alpha)
                    }
                    Draw2D.roundedRect(bx, sheetY - 2f, boxW, boxH, 3f, boxBg)

                    val noteText = noteName.substring(0, 1)
                    val tW = NanoVGRenderer.getTextWidth(noteText, 8.5f)
                    NanoVGRenderer.drawString(noteText, bx + (boxW - tW) * 0.5f, sheetY + 1.5f, NanoVGColor.WHITE.withAlpha(alpha), 8.5f)
                }
            }

            // Render White Keys
            for (i in whiteKeys.indices) {
                val key = whiteKeys[i]
                val kx = START_PIANO_X + i * WHITE_KEY_W
                val isHover = localMx in kx..(kx + WHITE_KEY_W) && localMy in PIANO_Y..(PIANO_Y + WHITE_KEY_H)
                val isPressed = now < key.pressUntil

                val keyBg = when {
                    isPressed -> theme.withAlpha(0.40f * alpha)
                    isHover -> NanoVGColor(0.92f, 0.94f, 0.98f, 0.28f * alpha)
                    else -> NanoVGColor(0.90f, 0.92f, 0.96f, 0.16f * alpha)
                }
                val pressOffset = if (isPressed) 2f else 0f

                Draw2D.roundedRect(kx + 1f, PIANO_Y + pressOffset, WHITE_KEY_W - 2f, WHITE_KEY_H - pressOffset, 5f, keyBg)
                Draw2D.roundedOutline(kx + 1f, PIANO_Y + pressOffset, WHITE_KEY_W - 2f, WHITE_KEY_H - pressOffset, 5f, NanoVGColor(1f, 1f, 1f, 0.18f * alpha), 1f)

                // Key label text
                val label = key.noteNum
                val lW = NanoVGRenderer.getTextWidth(label, 11f)
                NanoVGRenderer.drawString(label, kx + (WHITE_KEY_W - lW) * 0.5f, PIANO_Y + WHITE_KEY_H - 32f, NanoVGColor.WHITE.withAlpha(alpha), 11f)

                val subLabel = key.shortcut
                val sW = NanoVGRenderer.getTextWidth(subLabel, 8f)
                NanoVGRenderer.drawString(subLabel, kx + (WHITE_KEY_W - sW) * 0.5f, PIANO_Y + WHITE_KEY_H - 14f, NanoVGColor(0.60f, 0.65f, 0.75f, 0.70f * alpha), 8f)
            }

            // Render Black Keys
            for (b in blackKeys.indices) {
                val key = blackKeys[b]
                val baseIdx = blackKeyIndices[b]
                val kx = START_PIANO_X + (baseIdx + 1) * WHITE_KEY_W - BLACK_KEY_W * 0.5f
                val isHover = localMx in kx..(kx + BLACK_KEY_W) && localMy in PIANO_Y..(PIANO_Y + BLACK_KEY_H)
                val isPressed = now < key.pressUntil

                val keyBg = when {
                    isPressed -> theme.withAlpha(0.65f * alpha)
                    isHover -> NanoVGColor(0.18f, 0.20f, 0.28f, 0.95f * alpha)
                    else -> NanoVGColor(0.08f, 0.09f, 0.14f, 0.95f * alpha)
                }
                val pressOffset = if (isPressed) 2f else 0f

                Draw2D.roundedRect(kx, PIANO_Y + pressOffset, BLACK_KEY_W, BLACK_KEY_H - pressOffset, 4f, keyBg)
                Draw2D.roundedOutline(kx, PIANO_Y + pressOffset, BLACK_KEY_W, BLACK_KEY_H - pressOffset, 4f, if (isPressed) theme else NanoVGColor(0.4f, 0.5f, 0.7f, 0.35f * alpha), 1f)

                val subLabel = key.shortcut
                val sW = NanoVGRenderer.getTextWidth(subLabel, 8.5f)
                NanoVGRenderer.drawString(subLabel, kx + (BLACK_KEY_W - sW) * 0.5f, PIANO_Y + BLACK_KEY_H - 14f, NanoVGColor(0.75f, 0.80f, 0.90f, 0.85f * alpha), 8.5f)
            }

            nvg.transform.restore()
        }
    }

    override fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        if (button != 0) return false

        // Check black keys first (they sit on top of white keys)
        for (b in blackKeys.indices) {
            val baseIdx = blackKeyIndices[b]
            val kx = START_PIANO_X + (baseIdx + 1) * WHITE_KEY_W - BLACK_KEY_W * 0.5f
            if (mouseX in kx..(kx + BLACK_KEY_W) && mouseY in PIANO_Y..(PIANO_Y + BLACK_KEY_H)) {
                playKey(blackKeys[b])
                return true
            }
        }

        // Check white keys
        for (i in whiteKeys.indices) {
            val kx = START_PIANO_X + i * WHITE_KEY_W
            if (mouseX in kx..(kx + WHITE_KEY_W) && mouseY in PIANO_Y..(PIANO_Y + WHITE_KEY_H)) {
                playKey(whiteKeys[i])
                return true
            }
        }

        return false
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Check number shortcuts 1-8
        numberKeyMap[keyCode]?.let { idx ->
            if (idx in whiteKeys.indices) {
                playKey(whiteKeys[idx])
                return true
            }
        }

        // Check white key letter shortcuts
        for (key in whiteKeys) {
            if (key.keyCode == keyCode) {
                playKey(key)
                return true
            }
        }

        // Check black key letter shortcuts
        for (key in blackKeys) {
            if (key.keyCode == keyCode) {
                playKey(key)
                return true
            }
        }

        return false
    }
}
