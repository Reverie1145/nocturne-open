package cn.reverie.app.music.render

import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.AudioVisualizer
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGPath
import org.lwjgl.nanovg.NanoVG.*
import kotlin.math.*

/**
 * High-precision vector icon rendering using NanoVG path primitives.
 * Provides crisp, resolution-independent controls for the music UI.
 */
object CloudMusicIconRenderer {

    fun drawPlayIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        // Equilateral-proportioned play button geometry (width ≈ 0.85 * height)
        // Optical shift (+0.08 * size) so apex and base feel visually balanced in circles
        val s = size / 24.0f
        val r = (size * 0.12f).coerceIn(1.0f, 3.5f)

        val xBase = cx - 3.5f * s + r
        val xApex = cx + 5.5f * s - r * 1.15f
        val yTop = cy - 6.0f * s + r * 1.15f
        val yBottom = cy + 6.0f * s - r * 1.15f

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(xBase, yTop)
        path.lineTo(xApex, cy)
        path.lineTo(xBase, yBottom)
        path.close()
        path.fill(color)
        path.stroke(color, r * 2.0f)

        nvgRestore(context)
    }

    fun drawPauseIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val barW = size * 0.22f
        val barH = size * 0.95f
        val gap = size * 0.22f
        Draw2D.roundedRect(cx - gap - barW, cy - barH * 0.5f, barW, barH, 1.5f, color)
        Draw2D.roundedRect(cx + gap, cy - barH * 0.5f, barW, barH, 1.5f, color)
    }

    fun drawNextIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val s = size / 24.0f
        val r = (size * 0.10f).coerceIn(1.0f, 2.5f)
        val xBase = cx - 4.5f * s + r
        val xApex = cx + 1.5f * s - r
        val yTop = cy - 5.5f * s + r
        val yBottom = cy + 5.5f * s - r

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(xBase, yTop)
        path.lineTo(xApex, cy)
        path.lineTo(xBase, yBottom)
        path.close()
        path.fill(color)
        path.stroke(color, r * 2.0f)

        val barW = (size * 0.14f).coerceAtLeast(1.5f)
        val barH = size * 0.50f
        Draw2D.roundedRect(cx + 3.0f * s, cy - barH * 0.5f, barW, barH, barW * 0.5f, color)
        nvgRestore(context)
    }

    fun drawPrevIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        val s = size / 24.0f
        val r = (size * 0.10f).coerceIn(1.0f, 2.5f)
        val xBase = cx + 4.5f * s - r
        val xApex = cx - 1.5f * s + r
        val yTop = cy - 5.5f * s + r
        val yBottom = cy + 5.5f * s - r

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(xBase, yTop)
        path.lineTo(xApex, cy)
        path.lineTo(xBase, yBottom)
        path.close()
        path.fill(color)
        path.stroke(color, r * 2.0f)

        val barW = (size * 0.14f).coerceAtLeast(1.5f)
        val barH = size * 0.50f
        Draw2D.roundedRect(cx - 3.0f * s - barW, cy - barH * 0.5f, barW, barH, barW * 0.5f, color)
        nvgRestore(context)
    }

    fun drawHeartIcon(context: Long, cx: Float, cy: Float, size: Float, filled: Boolean, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 20f
        nvgScale(context, s, s)

        nvgLineJoin(context, NVG_ROUND)
        nvgLineCap(context, NVG_ROUND)

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(0f, 8.5f)
        // Left flank curving up to outer side
        path.bezierTo(-4.6f, 5.5f, -9.6f, 1.8f, -9.6f, -3.0f)
        // Left lobe curving over the top into center cleft
        path.bezierTo(-9.6f, -8.0f, -4.2f, -9.0f, 0f, -4.5f)
        // Right lobe curving over the top to outer side
        path.bezierTo(4.2f, -9.0f, 9.6f, -8.0f, 9.6f, -3.0f)
        // Right flank curving down into bottom tip
        path.bezierTo(9.6f, 1.8f, 4.6f, 5.5f, 0f, 8.5f)
        path.close()

        if (filled) {
            path.fill(color)
        } else {
            path.stroke(color, 1.8f / s)
        }
        nvgRestore(context)
    }

    fun drawVolumeIcon(context: Long, cx: Float, cy: Float, size: Float, vol: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 14f
        nvgScale(context, s, s)

        // Speaker cone
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(-6f, -3f)
        path.lineTo(-3f, -3f)
        path.lineTo(1f, -7f)
        path.lineTo(1f, 7f)
        path.lineTo(-3f, 3f)
        path.lineTo(-6f, 3f)
        path.close()
        path.fill(color)

        // Waves based on volume level
        if (vol > 0.05f) {
            nvgLineCap(context, NVG_ROUND)
            path.begin()
            nvgArc(context, 1f, 0f, 4.5f, -0.6f, 0.6f, NVG_CW)
            path.stroke(color, 1.5f)
        }
        if (vol > 0.50f) {
            path.begin()
            nvgArc(context, 1f, 0f, 8f, -0.7f, 0.7f, NVG_CW)
            path.stroke(color, 1.5f)
        }
        if (vol <= 0.01f) {
            // Mute X
            path.begin()
            path.moveTo(4f, -3f)
            path.lineTo(8f, 3f)
            path.stroke(color, 1.4f)
            path.begin()
            path.moveTo(8f, -3f)
            path.lineTo(4f, 3f)
            path.stroke(color, 1.4f)
        }
        nvgRestore(context)
    }

    fun drawLoopIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor, single: Boolean = false) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)
        nvgLineCap(context, NVG_ROUND)
        nvgLineJoin(context, NVG_ROUND)

        // Arrow loop top
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(-5f, 2f)
        path.lineTo(-5f, -3f)
        nvgArcTo(context, -5f, -6f, 0f, -6f, 3f)
        path.lineTo(5f, -6f)
        path.stroke(color, 1.6f)

        // Arrow tip top
        path.begin()
        path.moveTo(3f, -8.5f)
        path.lineTo(6f, -6f)
        path.lineTo(3f, -3.5f)
        path.stroke(color, 1.6f)

        // Arrow loop bottom
        path.begin()
        path.moveTo(5f, -2f)
        path.lineTo(5f, 3f)
        nvgArcTo(context, 5f, 6f, 0f, 6f, 3f)
        path.lineTo(-5f, 6f)
        path.stroke(color, 1.6f)

        // Arrow tip bottom
        path.begin()
        path.moveTo(-3f, 3.5f)
        path.lineTo(-6f, 6f)
        path.lineTo(-3f, 8.5f)
        path.stroke(color, 1.6f)

        if (single) {
            Draw2D.circle(0f, 0f, 3.5f, color)
        }
        nvgRestore(context)
    }

    fun drawShuffleIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)
        nvgLineCap(context, NVG_ROUND)

        val path = NanoVGPath(context)
        // Crossing line 1
        path.begin()
        path.moveTo(-7f, -4f)
        nvgBezierTo(context, -1f, -4f, 1f, 4f, 7f, 4f)
        path.stroke(color, 1.6f)

        // Arrow 1
        path.begin()
        path.moveTo(4.5f, 1.5f)
        path.lineTo(7.5f, 4f)
        path.lineTo(4.5f, 6.5f)
        path.stroke(color, 1.6f)

        // Crossing line 2
        path.begin()
        path.moveTo(-7f, 4f)
        nvgBezierTo(context, -2f, 4f, -1f, 2f, 0f, 0f)
        path.stroke(color, 1.6f)

        path.begin()
        path.moveTo(1.5f, -1.5f)
        nvgBezierTo(context, 3f, -4f, 5f, -4f, 7f, -4f)
        path.stroke(color, 1.6f)

        // Arrow 2
        path.begin()
        path.moveTo(4.5f, -6.5f)
        path.lineTo(7.5f, -4f)
        path.lineTo(4.5f, -1.5f)
        path.stroke(color, 1.6f)

        nvgRestore(context)
    }

    fun drawTabIcon(tab: cn.reverie.app.music.ui.CloudMusicScreen.MusicTab, context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        when (tab) {
            cn.reverie.app.music.ui.CloudMusicScreen.MusicTab.DISCOVER -> drawDiscoverIcon(context, cx, cy, size, color)
            cn.reverie.app.music.ui.CloudMusicScreen.MusicTab.FM -> drawFmIcon(context, cx, cy, size, color)
            cn.reverie.app.music.ui.CloudMusicScreen.MusicTab.PLAYLISTS -> drawPlaylistIcon(context, cx, cy, size, color)
            cn.reverie.app.music.ui.CloudMusicScreen.MusicTab.CHARTS -> drawChartIcon(context, cx, cy, size, color)
            cn.reverie.app.music.ui.CloudMusicScreen.MusicTab.FAVORITES -> drawHeartIcon(context, cx, cy, size, filled = true, color = color)
        }
    }

    fun drawDiscoverIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(0f, -7f)
        path.bezierTo(1.5f, -2.5f, 2.5f, -1.5f, 7f, 0f)
        path.bezierTo(2.5f, 1.5f, 1.5f, 2.5f, 0f, 7f)
        path.bezierTo(-1.5f, 2.5f, -2.5f, 1.5f, -7f, 0f)
        path.bezierTo(-2.5f, -1.5f, -1.5f, -2.5f, 0f, -7f)
        path.close()
        path.fill(color)
        nvgRestore(context)
    }

    fun drawFmIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)

        // Radio body
        Draw2D.roundedOutline(-6f, -3f, 12f, 9f, 2f, color, 1.5f)
        // Speaker grille dots / circle
        Draw2D.circle(-2f, 1.5f, 2.2f, color.multiplyAlpha(0.6f))
        // Tuning slider
        Draw2D.roundedRect(1.5f, -0.5f, 3f, 1.2f, 0.6f, color)
        Draw2D.roundedRect(1.5f, 2f, 3f, 1.2f, 0.6f, color)
        // Antenna
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(-4f, -3f)
        path.lineTo(4f, -7f)
        path.stroke(color, 1.4f)
        Draw2D.circle(4f, -7f, 1.2f, color)
        nvgRestore(context)
    }

    fun drawPlaylistIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)

        // Playlist lines
        Draw2D.roundedRect(-7f, -5f, 6.5f, 1.5f, 0.75f, color)
        Draw2D.roundedRect(-7f, -1f, 8f, 1.5f, 0.75f, color)
        Draw2D.roundedRect(-7f, 3f, 5.5f, 1.5f, 0.75f, color)

        // Musical eighth note
        val nx = 3.5f
        val ny = 3.5f
        Draw2D.circle(nx, ny, 2.2f, color)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(nx + 1.8f, ny)
        path.lineTo(nx + 1.8f, -5.5f)
        path.lineTo(nx + 4.8f, -4f)
        path.stroke(color, 1.4f)
        nvgRestore(context)
    }

    fun drawChartIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        val s = size / 16f
        nvgScale(context, s, s)

        // 3 stepped ranking bars (Rank 2, Rank 1, Rank 3)
        Draw2D.roundedRect(-6.5f, -1f, 3.5f, 7f, 1f, color.multiplyAlpha(0.70f))
        Draw2D.roundedRect(-1.75f, -5.5f, 3.5f, 11.5f, 1f, color)
        Draw2D.roundedRect(3f, 1.5f, 3.5f, 4.5f, 1f, color.multiplyAlpha(0.55f))
        // Star above rank 1
        Draw2D.circle(0f, -7.5f, 1.3f, color)
        nvgRestore(context)
    }

    fun drawUserIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val headR = size * 0.22f
        Draw2D.circle(cx, cy - size * 0.16f, headR, color)

        nvgSave(context)
        nvgLineCap(context, NVG_ROUND)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(cx - size * 0.38f, cy + size * 0.38f)
        path.bezierTo(cx - size * 0.32f, cy + size * 0.08f, cx + size * 0.32f, cy + size * 0.08f, cx + size * 0.38f, cy + size * 0.38f)
        path.stroke(color, 1.5f)
        nvgRestore(context)
    }

    fun drawSearchIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val r = size * 0.35f
        Draw2D.circleOutline(cx - r * 0.3f, cy - r * 0.3f, r, color, 1.6f)
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(cx + r * 0.45f, cy + r * 0.45f)
        path.lineTo(cx + size * 0.45f, cy + size * 0.45f)
        path.stroke(color, 1.8f)
    }

    fun drawCloseIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val d = size * 0.35f
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(cx - d, cy - d)
        path.lineTo(cx + d, cy + d)
        path.stroke(color, 1.6f)

        path.begin()
        path.moveTo(cx + d, cy - d)
        path.lineTo(cx - d, cy + d)
        path.stroke(color, 1.6f)
    }

    fun drawTrashIcon(context: Long, cx: Float, cy: Float, size: Float, color: NanoVGColor) {
        val w = size * 0.45f
        val h = size * 0.55f
        // Can body
        Draw2D.roundedOutline(cx - w, cy - h * 0.3f, w * 2f, h * 1.3f, 2f, color, 1.5f)
        // Can lid
        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(cx - w * 1.25f, cy - h * 0.3f)
        path.lineTo(cx + w * 1.25f, cy - h * 0.3f)
        path.stroke(color, 1.5f)
        // Lid handle
        path.begin()
        path.moveTo(cx - w * 0.4f, cy - h * 0.3f)
        path.lineTo(cx - w * 0.4f, cy - h * 0.65f)
        path.lineTo(cx + w * 0.4f, cy - h * 0.65f)
        path.lineTo(cx + w * 0.4f, cy - h * 0.3f)
        path.stroke(color, 1.5f)
    }

    fun drawEqualizerBars(
        cx: Float,
        cy: Float,
        width: Float,
        maxHeight: Float,
        animTime: Long,
        color: NanoVGColor,
        barCount: Int = 4
    ) {
        val totalSpacing = width / barCount
        val barW = (totalSpacing * 0.58f).coerceAtLeast(1.5f)
        val isRealAudioActive = CloudMusicManager.player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING

        for (i in 0 until barCount) {
            val h = if (isRealAudioActive) {
                val bandIdx = (i * (AudioVisualizer.BANDS / barCount)).coerceIn(0, AudioVisualizer.BANDS - 1)
                val rawBand = AudioVisualizer.bands[bandIdx]
                (rawBand * maxHeight).coerceIn(2.0f, maxHeight)
            } else {
                2.0f
            }
            val bx = cx - width * 0.5f + i * totalSpacing + (totalSpacing - barW) * 0.5f
            Draw2D.roundedRect(bx, cy + maxHeight * 0.5f - h, barW, h, barW * 0.5f, color)
        }
    }

    /**
     * Real-time audio oscilloscope rendering based on raw PCM waveform.
     */
    fun drawRealOscilloscopeWave(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: NanoVGColor,
        glow: Boolean = true
    ) {
        val wave = AudioVisualizer.getRenderWave()
        val points = wave.size
        if (points < 2) return

        val step = width / (points - 1).toFloat()
        val midY = y + height * 0.5f
        val amp = height * 0.44f

        val path = NanoVGPath(context)
        path.begin()
        path.moveTo(x, midY + wave[0] * amp)
        for (i in 1 until points) {
            path.lineTo(x + i * step, midY + wave[i] * amp)
        }
        if (glow) {
            path.stroke(color.withAlpha(color.a * 0.28f), 3.0f)
        }
        path.stroke(color, 1.4f)
    }

    /**
     * Real-time multi-band audio spectrum visualizer with peak hold dots.
     */
    fun drawRealSpectrumBars(
        context: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        barsCount: Int = 16,
        color: NanoVGColor,
        drawPeaks: Boolean = true
    ) {
        val count = barsCount.coerceIn(4, AudioVisualizer.BANDS)
        val step = width / count
        val barW = (step * 0.65f).coerceAtLeast(1.5f)

        for (i in 0 until count) {
            val bandVal = AudioVisualizer.bands[i]
            val peakVal = AudioVisualizer.peakBands[i]

            val barH = (bandVal * height).coerceIn(2.0f, height)
            val bx = x + i * step + (step - barW) * 0.5f
            val by = y + height - barH

            Draw2D.roundedRect(bx, by, barW, barH, barW * 0.5f, color)

            if (drawPeaks && peakVal > 0.05f) {
                val peakY = (y + height - peakVal * height - 2f).coerceAtLeast(y)
                Draw2D.roundedRect(bx, peakY, barW, 1.5f, 0.75f, color.withAlpha(0.95f))
            }
        }
    }

    fun drawVinylDisc(
        context: Long,
        cx: Float,
        cy: Float,
        radius: Float,
        rotationAngle: Float,
        coverUrl: String?
    ) {
        // 1. Vinyl disc outer shadow
        Draw2D.shadow(cx - radius, cy - radius, radius * 2f, radius * 2f, radius, 16f, NanoVGColor(0f, 0f, 0f, 0.65f))

        // 2. Vinyl black groove disc
        Draw2D.circle(cx, cy, radius, NanoVGColor(0.06f, 0.06f, 0.08f, 0.98f))
        Draw2D.circleOutline(cx, cy, radius, NanoVGColor(0.18f, 0.18f, 0.22f, 0.70f), 1.5f)

        // 3. Subtle groove rings
        val ringCount = 5
        for (i in 1..ringCount) {
            val r = radius * (0.45f + i * 0.10f)
            Draw2D.circleOutline(cx, cy, r, NanoVGColor(1f, 1f, 1f, 0.025f), 1.0f)
        }

        // 4. Center rotating cover art
        val innerR = radius * 0.38f
        nvgSave(context)
        nvgTranslate(context, cx, cy)
        nvgRotate(context, rotationAngle)

        CloudMusicImageCache.drawCover(coverUrl, -innerR, -innerR, innerR * 2f, innerR * 2f, innerR, 1.0f)

        // Center vinyl hole ring
        val holeR = radius * 0.08f
        Draw2D.circle(0f, 0f, holeR, NanoVGColor(0.04f, 0.04f, 0.06f, 1.0f))
        Draw2D.circleOutline(0f, 0f, holeR, NanoVGColor(1f, 1f, 1f, 0.20f), 1.5f)

        nvgRestore(context)
    }
}
