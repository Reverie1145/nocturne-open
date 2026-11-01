package cn.reverie.app.music.ui

import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.api.MusicTrack
import cn.reverie.app.music.api.PlayMode
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.app.music.render.CloudMusicIconRenderer
import cn.reverie.app.music.render.CloudMusicImageCache
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import kotlin.math.abs

/**
 * Right Detail Stage Renderer for Nocturne Music.
 * Styled after ClickGUI's Right Detail Panel (WindowClickGuiRenderer.renderDetails):
 * - Showcase header with album cover, soft shadow, marquee title, artist, album, quality badge, like button
 * - Synced real-time lyrics stage with karaoke glow highlight and translation
 * - Master audio controls: progress scrubber with time readout, loop/shuffle mode, previous, play/pause, next, volume
 */
internal fun CloudMusicScreen.renderDetails(mx: Float, my: Float, partialTicks: Float, highlight: NanoVGColor, alpha: Float) {
    val detailX = CloudMusicScreen.DETAIL_X
    val detailW = CloudMusicScreen.DETAIL_W

    // Detail panel rounded glass card (identical to ClickGUI DETAIL_X, 77f, DETAIL_W, 393f)
    NanoVGRenderer.drawRoundedRect(detailX, 77f, detailW, 393f, 9f,
        NanoVGColor(1f, 1f, 1f, 0.025f * alpha))
    NanoVGRenderer.drawRoundedOutline(detailX, 77f, detailW, 393f, 9f,
        NanoVGColor(1f, 1f, 1f, 0.065f * alpha), 0.8f)

    val player = CloudMusicManager.player
    val track = selectedTrack ?: player.currentTrack

    if (track == null) {
        NanoVGRenderer.drawCenteredString("Select or play a song", detailX + detailW * 0.5f, 248f,
            NanoVGColor(0.69f, 0.70f, 0.74f, alpha), 11f)
        return
    }

    detailTransitionAnim.animateTo(1f)
    val detailProgress = detailTransitionAnim.value
    val offsetX = (1f - detailProgress) * 10f

    val transform = RenderEngine.nanoVG.transform
    transform.save()
    transform.translate(offsetX, 0f)
    try {
        // --- Section A: Track Showcase & Cover Art (y = 85f .. 186f) ---
        renderDetailHeader(mx - offsetX, my, track, player, highlight, alpha)

        // Divider Line
        Draw2D.line(detailX + 16f, 188f, detailX + detailW - 16f, 188f,
            NanoVGColor(1f, 1f, 1f, 0.07f * alpha), 1f)

        // --- Section B: Real-Time Synced Lyrics (y = 192f .. 368f) ---
        if (sharePickerOpen) renderShareContactPanel(mx - offsetX, my, alpha)
        else renderLyricsStage(mx - offsetX, my, player, highlight, alpha)

        // Divider Line
        Draw2D.line(detailX + 16f, 370f, detailX + detailW - 16f, 370f,
            NanoVGColor(1f, 1f, 1f, 0.07f * alpha), 1f)

        // --- Section C: Playback Scrubber & Master Controls (y = 374f .. 465f) ---
        renderPlaybackControls(mx - offsetX, my, player, highlight, alpha)
        val shareX = detailX + 116f
        val shareY = 154f
        val shareHover = inside(mx - offsetX, my, shareX, shareY, 64f, 16f)
        NanoVGRenderer.drawRoundedRect(shareX, shareY, 64f, 16f, 4f,
            NanoVGColor(1f, 1f, 1f, (if (shareHover || sharePickerOpen) 0.12f else 0.055f) * alpha))
        NanoVGRenderer.drawRoundedOutline(shareX, shareY, 64f, 16f, 4f,
            NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 0.75f)
        NanoVGRenderer.drawCenteredString("Share via IRC", shareX + 32f, shareY + 8f,
            NanoVGColor(0.86f, 0.89f, 0.94f, alpha), 7f)
    } finally {
        transform.restore()
    }
}

private fun CloudMusicScreen.renderShareContactPanel(mx: Float, my: Float, alpha: Float) {
    val x = CloudMusicScreen.DETAIL_X + 16f
    val y = 194f
    val w = CloudMusicScreen.DETAIL_W - 32f
    val h = 172f
    NanoVGRenderer.drawRoundedRect(x, y, w, h, 6f, NanoVGColor(0f, 0f, 0f, 0.12f * alpha))
    NanoVGRenderer.drawString("CHOOSE AN IRC CONTACT", x + 10f, y + 8f, NanoVGColor(0.64f, 0.67f, 0.73f, alpha), 8f)
    val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(6)
    if (contacts.isEmpty()) {
        NanoVGRenderer.drawCenteredString("No online contacts", x + w * 0.5f, y + 78f, NanoVGColor(0.55f, 0.57f, 0.63f, alpha), 8f)
        return
    }
    contacts.forEachIndexed { index, nick ->
        val rowY = y + 25f + index * 23f
        val hovered = inside(mx, my, x + 6f, rowY, w - 12f, 21f)
        if (hovered) NanoVGRenderer.drawRoundedRect(x + 6f, rowY, w - 12f, 21f, 4f, NanoVGColor(1f, 1f, 1f, 0.075f * alpha))
        NanoVGRenderer.drawVerticallyCenteredString("@$nick", x + 13f, rowY + 10.5f, NanoVGColor(0.88f, 0.90f, 0.94f, alpha), 8f)
        NanoVGRenderer.drawRightVerticallyCenteredString("SEND", x + w - 13f, rowY + 10.5f, NanoVGColor(0.63f, 0.76f, 0.98f, alpha), 7f)
    }
}

internal fun CloudMusicScreen.renderDetailHeader(
    mx: Float, my: Float,
    track: MusicTrack, player: CloudMusicPlayer,
    highlight: NanoVGColor, alpha: Float
) {
    val detailX = CloudMusicScreen.DETAIL_X
    val detailW = CloudMusicScreen.DETAIL_W
    val coverX = detailX + 16f
    val coverY = 88f
    val coverSize = 88f

    // Cover drop shadow
    if (HUDModule.widgetShadow.value) {
        Draw2D.shadow(coverX, coverY + 3f, coverSize, coverSize, 8f, 16f,
            NanoVGColor(0f, 0f, 0f, 0.40f * alpha))
    }

    // Cover Art with rounded corners
    CloudMusicImageCache.drawCover(track.picUrl, coverX, coverY, coverSize, coverSize, radius = 8f, alpha = alpha)
    NanoVGRenderer.drawRoundedOutline(coverX, coverY, coverSize, coverSize, 8f,
        NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 1f)

    // Metadata on right (detailX + 116f)
    val metaX = detailX + 116f
    val metaW = detailW - 132f

    // Song Title (Marquee on hover)
    NanoVGRenderer.drawMarqueeString(
        track.name, metaX, 102f, metaW,
        inside(mx, my, metaX, 90f, metaW, 26f),
        NanoVGColor(0.96f, 0.97f, 0.99f, alpha), 13.5f
    )

    // Artist Name
    NanoVGRenderer.drawVerticallyCenteredString(
        track.artists, metaX, 122f,
        NanoVGColor(0.72f, 0.74f, 0.78f, alpha), 9.5f
    )

    // Album Name
    val albumLabel = if (track.albumName.isNotBlank()) "\"${track.albumName}\"" else "Single"
    NanoVGRenderer.withScissor(metaX, 132f, metaW - 30f, 18f) {
        NanoVGRenderer.drawVerticallyCenteredString(
            albumLabel, metaX, 140f,
            NanoVGColor(0.50f, 0.52f, 0.56f, alpha), 8.5f
        )
    }

    // Quality Pill [Lossless/320K]
    NanoVGRenderer.drawRoundedRect(metaX + 70f, 154f, 54f, 16f, 4f,
        NanoVGColor(1f, 1f, 1f, 0.05f * alpha))
    NanoVGRenderer.drawCenteredString("320K HQ", metaX + 97f, 162f,
        NanoVGColor(0.75f, 0.77f, 0.82f, alpha), 7.5f)

    // Like / Favorite Heart Button
    val isLiked = CloudMusicManager.isSongLiked(track.id)
    val heartX = metaX + metaW - 12f
    val heartY = 162f
    val heartHovered = inside(mx, my, heartX - 12f, heartY - 12f, 24f, 24f)

    if (heartHovered) {
        NanoVGRenderer.drawRoundedRect(heartX - 11f, heartY - 11f, 22f, 22f, 5f,
            NanoVGColor(1f, 1f, 1f, 0.07f * alpha))
    }

    val heartColor = if (isLiked) NanoVGColor(0.96f, 0.25f, 0.38f, alpha)
    else if (heartHovered) highlight.withAlpha(alpha)
    else NanoVGColor(0.65f, 0.67f, 0.72f, alpha)

    val heartScale = heartPopAnim.value
    CloudMusicIconRenderer.drawHeartIcon(
        RenderEngine.nanoVG.handle, heartX, heartY, 15f * heartScale,
        filled = isLiked, color = heartColor
    )
}

internal fun CloudMusicScreen.renderLyricsStage(
    mx: Float, my: Float,
    player: CloudMusicPlayer,
    highlight: NanoVGColor, alpha: Float
) {
    val detailX = CloudMusicScreen.DETAIL_X
    val detailW = CloudMusicScreen.DETAIL_W
    val stageX = detailX + 16f
    val stageY = 194f
    val stageW = detailW - 32f
    val stageH = 172f

    // Lyrics Header Tag
    NanoVGRenderer.drawString("LYRICS", stageX, stageY + 2f,
        NanoVGColor(0.55f, 0.57f, 0.62f, alpha), 8f)

    val lyricData = player.currentLyric
    val lines = lyricData.lines

    if (lines.isEmpty()) {
        NanoVGRenderer.drawCenteredString("Instrumental music, please enjoy", stageX + stageW * 0.5f, stageY + stageH * 0.5f - 6f,
            NanoVGColor(0.72f, 0.74f, 0.78f, alpha), 11f)
        NanoVGRenderer.drawCenteredString("Music is the universal language", stageX + stageW * 0.5f, stageY + stageH * 0.5f + 14f,
            NanoVGColor(0.42f, 0.44f, 0.48f, alpha), 8.5f)
        return
    }

    val curIdx = lyricData.findLineIndex(player.currentPositionMs)
    val targetScroll = curIdx * 34f
    val prevScroll = lyricScroll
    lyricScroll += (targetScroll - lyricScroll) * 0.18f
    val scrollVelocity = abs(lyricScroll - prevScroll)
    val motionBlurAmount = (scrollVelocity * 0.8f).coerceIn(0f, 3.5f)

    val centerY = stageY + stageH * 0.5f
    val maxPixelDist = (stageH * 0.44f).coerceAtLeast(60f)

    NanoVGRenderer.withScissor(stageX, stageY + 12f, stageW, stageH - 14f) {
        val minIdx = (curIdx - 4).coerceAtLeast(0)
        val maxIdx = (curIdx + 4).coerceAtMost(lines.size - 1)
        for (i in minIdx..maxIdx) {
            val line = lines[i]
            val lineY = centerY + (i * 34f - lyricScroll)
            val pixelDist = abs(lineY - centerY)

            if (pixelDist > maxPixelDist * 1.25f) continue

            // Continuous pixel-based blur: center is sharp, increasing smoothly towards the viewport edges
            val pixelRatio = (pixelDist / maxPixelDist).coerceIn(0f, 1f)
            val pixelBlur = (pixelRatio * 2.2f + motionBlurAmount).coerceIn(0f, 2.8f)
            val lineAlpha = (1.0f - pixelRatio * 0.62f).coerceIn(0.25f, 1.0f) * alpha
            val fontSize = (11.8f - pixelRatio * 2.2f).coerceAtLeast(9.0f)

            val isCenter = pixelRatio < 0.15f
            val textColor = if (isCenter) {
                NanoVGColor.WHITE.withAlpha(lineAlpha)
            } else {
                NanoVGColor(0.74f, 0.76f, 0.82f, lineAlpha)
            }

            val hasTrans = line.translation.isNotBlank() && isCenter
            val activeY = if (hasTrans) lineY - 6f else lineY

            NanoVGRenderer.drawCenteredString(
                line.text, stageX + stageW * 0.5f, activeY,
                textColor, fontSize,
                shadow = true,
                blur = pixelBlur
            )

            if (hasTrans) {
                NanoVGRenderer.drawCenteredString(
                    line.translation, stageX + stageW * 0.5f, activeY + 14f,
                    NanoVGColor(0.80f, 0.85f, 0.95f, 0.85f * lineAlpha), 8.5f,
                    blur = motionBlurAmount * 0.5f
                )
            }
        }
    }
}

internal fun CloudMusicScreen.renderPlaybackControls(
    mx: Float, my: Float,
    player: CloudMusicPlayer,
    highlight: NanoVGColor, alpha: Float
) {
    val detailX = CloudMusicScreen.DETAIL_X
    val detailW = CloudMusicScreen.DETAIL_W
    val barX = detailX + 18f
    val barY = 384f
    val barW = detailW - 36f
    val barH = 3.5f

    val durMs = maxOf(1L, player.durationMs)
    val curMs = if (isDraggingProgress) dragProgressMs else player.currentPositionMs
    val progressRatio = (curMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f)

    // Progress Rail & Knob
    val barHovered = inside(mx, my, barX - 4f, barY - 6f, barW + 8f, 16f)
    val activeBarH = if (barHovered || isDraggingProgress) 5f else 3.5f

    NanoVGRenderer.drawRoundedRect(barX, barY - (activeBarH - barH) * 0.5f, barW, activeBarH, activeBarH * 0.5f,
        NanoVGColor(1f, 1f, 1f, 0.10f * alpha))
    NanoVGRenderer.drawRoundedRect(barX, barY - (activeBarH - barH) * 0.5f, barW * progressRatio, activeBarH, activeBarH * 0.5f,
        highlight.withAlpha(0.90f * alpha))

    if (barHovered || isDraggingProgress) {
        val knobX = barX + barW * progressRatio
        NanoVGRenderer.drawCircle(knobX, barY + barH * 0.5f, 5f, highlight.withAlpha(alpha))
    }

    // Time labels (placed below progress rail with clean margin from divider line)
    val timeY = 396f
    NanoVGRenderer.drawString(formatTime(curMs), barX, timeY,
        NanoVGColor(0.52f, 0.54f, 0.60f, alpha), 7.5f)
    NanoVGRenderer.drawRightString(formatTime(durMs), barX + barW, timeY,
        NanoVGColor(0.52f, 0.54f, 0.60f, alpha), 7.5f)

    // Master Control Buttons (y = 432f)
    val btnY = 432f

    // 1. Loop / PlayMode Toggle (detailX + 32f)
    val modeX = detailX + 32f
    val modeHovered = inside(mx, my, modeX - 14f, btnY - 14f, 28f, 28f)
    if (modeHovered) {
        NanoVGRenderer.drawCircle(modeX, btnY, 13f, NanoVGColor(1f, 1f, 1f, 0.06f * alpha))
    }
    val modeColor = if (modeHovered) highlight.withAlpha(alpha) else NanoVGColor(0.65f, 0.67f, 0.72f, alpha)

    when (player.playMode) {
        PlayMode.LOOP_ALL -> CloudMusicIconRenderer.drawLoopIcon(RenderEngine.nanoVG.handle, modeX, btnY, 13f, modeColor, single = false)
        PlayMode.SINGLE_LOOP -> CloudMusicIconRenderer.drawLoopIcon(RenderEngine.nanoVG.handle, modeX, btnY, 13f, highlight.withAlpha(alpha), single = true)
        PlayMode.SHUFFLE -> CloudMusicIconRenderer.drawShuffleIcon(RenderEngine.nanoVG.handle, modeX, btnY, 13f, highlight.withAlpha(alpha))
    }

    // 2. Previous Track (detailX + 76f)
    val prevX = detailX + 76f
    val prevHovered = inside(mx, my, prevX - 14f, btnY - 14f, 28f, 28f)
    if (prevHovered) {
        NanoVGRenderer.drawCircle(prevX, btnY, 13f, NanoVGColor(1f, 1f, 1f, 0.06f * alpha))
    }
    CloudMusicIconRenderer.drawPrevIcon(
        RenderEngine.nanoVG.handle, prevX, btnY, 14f,
        if (prevHovered) highlight else NanoVGColor(0.72f, 0.74f, 0.78f, alpha)
    )

    // 3. Play / Pause Central Hero Button (detailX + 124f)
    val playX = detailX + 124f
    val isPlaying = player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING
    val playHovered = inside(mx, my, playX - 17f, btnY - 17f, 34f, 34f)
    playBtnHoverAnim.animateTo(if (playHovered) 1f else 0f)

    val playR = 16f + 1f * playBtnHoverAnim.value
    Draw2D.shadow(playX - playR, btnY - playR + 2f, playR * 2f, playR * 2f, playR, 12f,
        NanoVGColor(1f, 1f, 1f, 0.15f * alpha))
    NanoVGRenderer.drawCircle(playX, btnY, playR, highlight.withAlpha((0.90f + playBtnHoverAnim.value * 0.10f) * alpha))

    val iconColor = NanoVGColor(0.08f, 0.09f, 0.12f, 1f)
    if (isPlaying) {
        CloudMusicIconRenderer.drawPauseIcon(RenderEngine.nanoVG.handle, playX, btnY, 13f, iconColor)
    } else {
        CloudMusicIconRenderer.drawPlayIcon(RenderEngine.nanoVG.handle, playX + 1f, btnY, 13f, iconColor)
    }

    // 4. Next Track (detailX + 172f)
    val nextX = detailX + 172f
    val nextHovered = inside(mx, my, nextX - 14f, btnY - 14f, 28f, 28f)
    if (nextHovered) {
        NanoVGRenderer.drawCircle(nextX, btnY, 13f, NanoVGColor(1f, 1f, 1f, 0.06f * alpha))
    }
    CloudMusicIconRenderer.drawNextIcon(
        RenderEngine.nanoVG.handle, nextX, btnY, 14f,
        if (nextHovered) highlight else NanoVGColor(0.72f, 0.74f, 0.78f, alpha)
    )

    // 5. Volume Speaker Icon & Slider (x = detailX + 220f .. 312f)
    val spkX = detailX + 220f
    val spkHovered = inside(mx, my, spkX - 11f, btnY - 11f, 22f, 22f)
    if (spkHovered) {
        NanoVGRenderer.drawCircle(spkX, btnY, 11f, NanoVGColor(1f, 1f, 1f, 0.06f * alpha))
    }
    CloudMusicIconRenderer.drawVolumeIcon(
        RenderEngine.nanoVG.handle, spkX, btnY, 13f, player.volume,
        if (spkHovered) highlight else NanoVGColor(0.68f, 0.70f, 0.75f, alpha)
    )

    val volRailX = detailX + 238f
    val volRailY = btnY - 2f
    val volRailW = 74f
    val volRailH = 4f
    val volHovered = inside(mx, my, volRailX - 4f, volRailY - 6f, volRailW + 8f, 16f)

    NanoVGRenderer.drawRoundedRect(volRailX, volRailY, volRailW, volRailH, 2f,
        NanoVGColor(1f, 1f, 1f, 0.10f * alpha))
    NanoVGRenderer.drawRoundedRect(volRailX, volRailY, volRailW * player.volume, volRailH, 2f,
        highlight.withAlpha(0.85f * alpha))

    if (volHovered || isDraggingVolume) {
        val knobX = volRailX + volRailW * player.volume
        NanoVGRenderer.drawCircle(knobX, btnY, 4f, highlight.withAlpha(alpha))
    }
}

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
