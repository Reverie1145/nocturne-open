package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.module.ModeSetting
import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.AudioVisualizer
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.app.music.render.CloudMusicIconRenderer
import cn.reverie.app.music.render.CloudMusicImageCache
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HudElement
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import kotlin.math.abs
import kotlin.math.sin

/**
 * Next-Gen Aesthetic Music Info HUD Widget.
 * Features:
 * - Luxury glassmorphic card with ambient beat-reactive glow
 * - Real audio spectrum visualizer & real-time oscilloscope waveform
 * - Rotating vinyl record mode with inertia deceleration
 * - Live playback status badge, marquee track title, and neon playhead
 */
class MusicHUDWidget : HudElement(
    name = "Music HUD",
    defaultX = 12f,
    defaultY = 46f,
    width = 246f,
    height = 56f,
    defaultEnabled = true
) {

    val visualStyle = ModeSetting("Style", "Visual layout of the info card", "Modern Glass",
        listOf("Modern Glass", "Oscilloscope", "Vinyl Record"))
    val showCover = BooleanSetting("Show Cover", "Display album artwork thumbnail", true)
    val showVisualizer = BooleanSetting("Show Spectrum", "Display real-time audio visualizer", true)
    val bassPulse = BooleanSetting("Bass Pulse", "Pulsate cover art to real audio bass kicks", true)
    val showTime = BooleanSetting("Show Time", "Display elapsed and total track duration", true)
    val showProgressBar = BooleanSetting("Show Progress", "Display smooth playback progress rail", true)

    private var animatedProgress = 0f
    private var vinylAngle = 0f
    private var vinylSpeed = 0f

    init {
        addSetting(visualStyle)
        addSetting(showCover)
        addSetting(showVisualizer)
        addSetting(bassPulse)
        addSetting(showTime)
        addSetting(showProgressBar)
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        val player = CloudMusicManager.player
        val track = player.currentTrack
        val isPlaying = player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING

        if (!isEditing && track == null) {
            AudioVisualizer.onIdleTick()
            return
        }

        // Update rotating vinyl physics with inertia deceleration
        val targetSpeed = if (isPlaying) 0.038f else 0f
        vinylSpeed += (targetSpeed - vinylSpeed) * 0.08f
        vinylAngle += vinylSpeed

        val renderTrackName = track?.name ?: "Nocturne In-Game Music"
        val renderArtist = track?.artists ?: "Frederic Chopin"
        val coverUrl = track?.picUrl
        val accent = getAccentColor()

        // Time & progress interpolation
        val currentMs = if (track != null) player.currentPositionMs else 84_000L
        val totalMs = if (track != null) maxOf(1L, player.durationMs) else 210_000L
        val targetProgress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
        animatedProgress += (targetProgress - animatedProgress) * 0.18f
        if (abs(targetProgress - animatedProgress) < 0.001f) {
            animatedProgress = targetProgress
        }

        val elapsedMin = (currentMs / 1000) / 60
        val elapsedSec = (currentMs / 1000) % 60
        val totalMin = (totalMs / 1000) / 60
        val totalSec = (totalMs / 1000) % 60
        val elapsedStr = "%02d:%02d".format(elapsedMin, elapsedSec)
        val totalStr = "%02d:%02d".format(totalMin, totalSec)

        val rx = x
        val ry = y
        val rw = width
        val rh = height

        // 1. Frosted Glass Backdrop & Ambient Shadow
        val bass = if (isPlaying && bassPulse.value) AudioVisualizer.bassEnergy else 0f
        val shadowAlpha = 0.40f + bass * 0.20f
        val shadowBlur = 10f + bass * 8f

        drawWidgetShadow(rx, ry, rw, rh, 10f, alpha = shadowAlpha, blur = shadowBlur)
        drawWidgetBackdrop(rx, ry, rw, rh, 10f)

        // Subtle gradient top rim
        Draw2D.roundedOutline(rx, ry, rw, rh, 10f,
            NanoVGColor(1f, 1f, 1f, 0.08f + bass * 0.08f), 1.0f)

        // 2. Cover Artwork / Vinyl Record
        val coverSize = 40f
        val coverX = rx + 8f
        val coverY = ry + 8f
        var textStartX = coverX + coverSize + 10f

        if (showCover.value) {
            if (visualStyle.isCurrent("Vinyl Record")) {
                // Vinyl disc peeking out behind jacket
                val vinylRadius = 18f
                val discX = coverX + coverSize - 2f
                val discY = coverY + coverSize * 0.5f
                CloudMusicIconRenderer.drawVinylDisc(
                    RenderEngine.nanoVG.handle, discX, discY, vinylRadius, vinylAngle, coverUrl
                )
                textStartX += 10f
            }

            // Cover beat-reactive shadow
            if (bass > 0.05f) {
                Draw2D.shadow(coverX, coverY, coverSize, coverSize, 7f, 10f + bass * 6f,
                    accent.withAlpha(0.50f * bass))
            }

            // Cover jacket
            if (coverUrl != null) {
                CloudMusicImageCache.drawCover(coverUrl, coverX, coverY, coverSize, coverSize, 7f, 1.0f)
            } else {
                Draw2D.roundedRect(coverX, coverY, coverSize, coverSize, 7f, NanoVGColor(1f, 1f, 1f, 0.07f))
                CloudMusicIconRenderer.drawDiscoverIcon(RenderEngine.context, coverX + 20f, coverY + 20f, 18f, accent)
            }
            Draw2D.roundedOutline(coverX, coverY, coverSize, coverSize, 7f,
                NanoVGColor(1f, 1f, 1f, 0.14f), 0.8f)
        }

        // 3. Real Audio Spectrum / Oscilloscope
        var rightPadding = 12f
        if (showVisualizer.value) {
            if (visualStyle.isCurrent("Oscilloscope")) {
                // Real-time smooth oscilloscope curve in corner
                val oscW = 50f
                val oscH = 16f
                val oscX = rx + rw - rightPadding - oscW
                val oscY = ry + 12f
                CloudMusicIconRenderer.drawRealOscilloscopeWave(
                    RenderEngine.context, oscX, oscY, oscW, oscH, accent
                )
                rightPadding += oscW + 8f
            } else {
                // Real 6-band spectrum visualizer
                val specW = 30f
                val specH = 16f
                val specX = rx + rw - rightPadding - specW
                val specY = ry + 12f
                CloudMusicIconRenderer.drawRealSpectrumBars(
                    RenderEngine.context, specX, specY, specW, specH, barsCount = 6, color = accent
                )
                rightPadding += specW + 8f
            }
        }

        // 4. Track Title & Artist (Clean luxury typography without redundant tags)
        val maxTitleW = (rx + rw - rightPadding) - textStartX
        NanoVGRenderer.drawMarqueeString(
            renderTrackName, textStartX, ry + 11.0f, maxTitleW.coerceAtLeast(40f),
            true, NanoVGColor.WHITE, 10.5f,
            shadow = textShadow
        )

        NanoVGRenderer.drawMarqueeString(
            renderArtist, textStartX, ry + 24.5f, maxTitleW.coerceAtLeast(40f),
            true, NanoVGColor(0.70f, 0.73f, 0.82f, 0.85f), 8.5f,
            shadow = textShadow
        )

        // 6. Modern Bottom Progress Bar & Time Readouts
        if (showProgressBar.value) {
            val barY = ry + rh - 9f
            val timeFont = 7.0f

            if (showTime.value) {
                // Time on left
                NanoVGRenderer.drawString(
                    elapsedStr, textStartX, barY - 1f,
                    NanoVGColor(0.55f, 0.58f, 0.65f, 0.85f), timeFont, shadow = textShadow
                )

                // Rail in between
                val railStartX = textStartX + 26f
                val railEndX = rx + rw - 36f
                val railW = (railEndX - railStartX).coerceAtLeast(20f)
                val railH = 2.5f

                Draw2D.roundedRect(railStartX, barY + 1f, railW, railH, 1.25f, NanoVGColor(1f, 1f, 1f, 0.08f))

                val fillW = (railW * animatedProgress).coerceIn(0f, railW)
                if (fillW > 0.5f) {
                    Draw2D.roundedRect(railStartX, barY + 1f, fillW, railH, 1.25f, accent)
                    // Neon glowing head
                    val tipX = railStartX + fillW
                    val headR = 2.0f + bass * 1.5f
                    Draw2D.circle(tipX, barY + 2.25f, headR, accent)
                }

                // Time on right
                NanoVGRenderer.drawRightString(
                    totalStr, rx + rw - 8f, barY - 1f,
                    NanoVGColor(0.55f, 0.58f, 0.65f, 0.85f), timeFont, shadow = textShadow
                )
            } else {
                // Full-width progress rail
                val railStartX = textStartX
                val railW = (rx + rw - 10f) - railStartX
                val railH = 2.5f

                Draw2D.roundedRect(railStartX, barY + 1f, railW, railH, 1.25f, NanoVGColor(1f, 1f, 1f, 0.08f))
                val fillW = (railW * animatedProgress).coerceIn(0f, railW)
                if (fillW > 0.5f) {
                    Draw2D.roundedRect(railStartX, barY + 1f, fillW, railH, 1.25f, accent)
                }
            }
        }
    }
}
