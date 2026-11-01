package cn.reverie.app.music.ui

import cn.reverie.gui.clickgui.CategoryVectorIcons
import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.app.music.render.CloudMusicIconRenderer
import cn.reverie.app.music.render.CloudMusicImageCache
import cn.reverie.render.animation.MotionBlurHelper
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import org.lwjgl.nanovg.NanoVG
import kotlin.math.abs
import kotlin.math.sin

/**
 * Window & List Renderer for Nocturne Music.
 * Styled after ClickGUI's Header, Sidebar, and Module List:
 * - Header with Nocturne Music brand, search bar focus animation, account status, close button
 * - Sidebar with 148px width, gliding rounded selection pill, project resource icons, mini playing card
 * - Left List with ClickGUI module-button card style, staggered entry animation, scrollbar
 */
internal fun CloudMusicScreen.renderHeader(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float) {
    val entranceBlur = MotionBlurHelper.getBlurForProgress(openAnim.value, maxBlur = 4.0f)

    // Brand Title
    NanoVGRenderer.drawString("NOCTURNE", 20f, 23f,
        NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 12f, blur = entranceBlur)

    // Music Tag Capsule
    NanoVGRenderer.drawRoundedRect(88f, 22f, 42f, 17f, 4.5f,
        NanoVGColor(1f, 1f, 1f, 0.06f * alpha))
    NanoVGRenderer.drawCenteredString("MUSIC", 109f, 30.5f,
        NanoVGColor(0.80f, 0.82f, 0.88f, alpha), 8.5f, blur = entranceBlur)

    // Search Box (identical to ClickGUI WindowClickGuiRenderer)
    val searchX = 183f
    val searchHovered = inside(mx, my, searchX, 16f, 342f, 29f)
    searchFocusAnim.animateTo(if (searchFocused || searchHovered) 1f else 0f)
    val searchW = 330f + 12f * searchFocusAnim.value

    NanoVGRenderer.drawRoundedRect(searchX, 16f, searchW, 29f, 7f,
        NanoVGColor(0f, 0f, 0f, (0.22f + searchFocusAnim.value * 0.10f) * alpha))
    NanoVGRenderer.drawRoundedOutline(searchX, 16f, searchW, 29f, 7f,
        NanoVGColor(1f, 1f, 1f, (0.10f + searchFocusAnim.value * 0.12f) * alpha), 1f)

    CategoryVectorIcons.drawSearchIcon(searchX + 11f, 25f, 10f,
        NanoVGColor(0.62f, 0.64f, 0.68f, alpha))

    val searchLabel = if (search.isEmpty()) "Search songs, artists, playlists..." else search
    NanoVGRenderer.withScissor(searchX + 30f, 18f, searchW - 44f, 24f) {
        NanoVGRenderer.drawVerticallyCenteredString(
            searchLabel, searchX + 31f, 30.5f,
            if (search.isEmpty()) NanoVGColor(0.50f, 0.51f, 0.55f, alpha)
            else NanoVGColor(0.94f, 0.95f, 0.97f, alpha), 10f
        )
    }

    // Blinking cursor when focused
    if (searchFocused && System.currentTimeMillis() / 500L % 2L == 0L) {
        val cursorX = (searchX + 32f + NanoVGRenderer.getTextWidth(search, 10f)).coerceAtMost(searchX + searchW - 13f)
        Draw2D.line(cursorX, 23f, cursorX, 38f, highlight.withAlpha(alpha), 1f)
    }

    // Clear search "×"
    if (search.isNotEmpty()) {
        val clearHovered = inside(mx, my, searchX + searchW - 20f, 20f, 18f, 20f)
        NanoVGRenderer.drawCenteredString("×", searchX + searchW - 14f, 30f,
            if (clearHovered) NanoVGColor.WHITE.withAlpha(alpha) else NanoVGColor(0.70f, 0.71f, 0.75f, alpha), 15f)
    }

    // User Account / QR Login Pill (x = 595f, w = 150f)
    val user = CloudMusicManager.api.currentUser
    val accX = 595f
    val accW = 150f
    val accHovered = inside(mx, my, accX, 17f, accW, 27f)

    NanoVGRenderer.drawRoundedRect(accX, 17f, accW, 27f, 6f,
        if (accHovered) highlight.withAlpha(0.12f * alpha)
        else NanoVGColor(1f, 1f, 1f, 0.045f * alpha))
    NanoVGRenderer.drawRoundedOutline(accX, 17f, accW, 27f, 6f,
        NanoVGColor(1f, 1f, 1f, (if (accHovered) 0.20f else 0.08f) * alpha), 0.8f)

    if (user != null) {
        if (user.avatarUrl.isNotBlank()) {
            CloudMusicImageCache.drawCover(user.avatarUrl, accX + 6f, 21.5f, 18f, 18f, radius = 9f, alpha = alpha)
        } else {
            CloudMusicIconRenderer.drawUserIcon(RenderEngine.nanoVG.handle, accX + 16f, 30.5f, 13f, highlight)
        }
        val maxNameW = if (user.isVip) accW - 56f else accW - 34f
        NanoVGRenderer.withScissor(accX + 28f, 18f, maxNameW, 25f) {
            NanoVGRenderer.drawVerticallyCenteredString(user.name, accX + 29f, 30.5f,
                NanoVGColor(0.88f, 0.90f, 0.94f, alpha), 9f)
        }
        if (user.isVip) {
            NanoVGRenderer.drawRoundedRect(accX + accW - 24f, 23f, 18f, 14f, 3f,
                NanoVGColor(0.95f, 0.78f, 0.25f, 0.25f * alpha))
            NanoVGRenderer.drawCenteredString("VIP", accX + accW - 15f, 30f,
                NanoVGColor(0.98f, 0.82f, 0.35f, alpha), 7.5f)
        }
    } else {
        CloudMusicIconRenderer.drawUserIcon(RenderEngine.nanoVG.handle, accX + 18f, 30.5f, 13f,
            NanoVGColor(0.80f, 0.82f, 0.86f, alpha))
        NanoVGRenderer.drawVerticallyCenteredString("Sign in", accX + 34f, 30.5f,
            NanoVGColor(0.83f, 0.85f, 0.89f, alpha), 9.5f)
    }

    // Close Button (exact ClickGUI coordinates)
    closeHoverAnim.animateTo(if (inside(mx, my, 755f, 17f, 22f, 27f)) 1f else 0f)
    NanoVGRenderer.drawRoundedRect(755f, 17f, 22f, 27f, 6f,
        NanoVGColor(1f, 1f, 1f, (0.06f + closeHoverAnim.value * 0.10f) * alpha))
    NanoVGRenderer.drawCenteredString("×", 766f, 30.5f,
        NanoVGColor(0.78f + closeHoverAnim.value * 0.18f, 0.79f + closeHoverAnim.value * 0.18f,
            0.82f + closeHoverAnim.value * 0.18f, alpha), 17f)
}

internal fun CloudMusicScreen.renderSidebar(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float) {
    val rowH = CloudMusicScreen.SIDEBAR_ROW_H
    val rowCenter = rowH * 0.5f
    var y = CloudMusicScreen.SIDEBAR_Y

    for (tab in CloudMusicScreen.MusicTab.entries) {
        val hovered = inside(mx, my, 10f, y, 129f, rowH)
        val hoverAnim = tabHoverMotions.getValue(tab)
        val selectAnim = tabSelectMotions.getValue(tab)
        hoverAnim.animateTo(if (hovered) 1f else 0f)
        selectAnim.animateTo(if (selectedTab == tab && search.isEmpty()) 1f else 0f)

        val hover = hoverAnim.value
        val selected = selectAnim.value
        val emphasis = maxOf(hover, selected)

        if (emphasis > 0.01f) {
            NanoVGRenderer.drawRoundedRect(10f, y, 129f, rowH, 5f,
                NanoVGColor(1f, 1f, 1f, (0.05f * hover + 0.09f * selected) * alpha))
        }

        if (selected > 0.02f) {
            NanoVGRenderer.drawRoundedRect(10f, y + 6f, 2.5f, rowH - 12f, 1.25f,
                highlight.withAlpha(0.90f * selected * alpha))
        }

        val iconColor = NanoVGColor(0.65f, 0.66f, 0.70f, alpha)
            .lerp(highlight.withAlpha(alpha), selected)
        CloudMusicIconRenderer.drawTabIcon(tab, RenderEngine.nanoVG.handle, 28f, y + rowCenter, 14f, iconColor)

        val textColor = NanoVGColor(0.70f, 0.71f, 0.75f, alpha)
            .lerp(NanoVGColor(0.97f, 0.98f, 1f, alpha), selected)
        NanoVGRenderer.drawVerticallyCenteredString(tab.title, 45f, y + rowCenter, textColor, 9.5f)

        y += CloudMusicScreen.SIDEBAR_ROW_STEP
    }

    // Bottom Mini Playing Card in Sidebar (y = 405f, h = 68f)
    val miniY = 405f
    val miniH = 68f
    val miniW = 129f
    NanoVGRenderer.drawRoundedRect(10f, miniY, miniW, miniH, 7f,
        NanoVGColor(1f, 1f, 1f, 0.035f * alpha))
    NanoVGRenderer.drawRoundedOutline(10f, miniY, miniW, miniH, 7f,
        NanoVGColor(1f, 1f, 1f, 0.06f * alpha), 0.8f)

    val player = CloudMusicManager.player
    val track = player.currentTrack
    val isPlaying = player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING

    if (track != null) {
        val discX = 26f
        val discY = miniY + 22f
        renderMiniVinyl(discX, discY, 13f, track.picUrl, isPlaying, alpha)

        NanoVGRenderer.withScissor(46f, miniY + 10f, miniW - 52f, 18f) {
            NanoVGRenderer.drawMarqueeString(track.name, 46f, miniY + 18f,
                miniW - 52f, inside(mx, my, 10f, miniY, miniW, miniH),
                NanoVGColor(0.92f, 0.93f, 0.96f, alpha), 8.5f)
        }

        NanoVGRenderer.drawVerticallyCenteredString(track.artists, 46f, miniY + 30f,
            NanoVGColor(0.58f, 0.60f, 0.64f, alpha), 7.5f)

        val eqX = 20f
        val eqY = miniY + 54f
        val time = System.currentTimeMillis() * 0.007f
        for (i in 0..6) {
            val wave = if (isPlaying) (sin(time * 2.2f + i * 1.1f).coerceAtLeast(0f) * 8f + 2f) else 2f
            NanoVGRenderer.drawRoundedRect(eqX + i * 5f, eqY - wave, 2.5f, wave, 1f,
                highlight.withAlpha((0.35f + (i % 3) * 0.15f) * alpha))
        }

        val miniBtnHovered = inside(mx, my, 10f + miniW - 32f, miniY + 40f, 22f, 20f)
        NanoVGRenderer.drawRoundedRect(10f + miniW - 32f, miniY + 40f, 22f, 20f, 4f,
            NanoVGColor(1f, 1f, 1f, (if (miniBtnHovered) 0.12f else 0.05f) * alpha))
        val miniBtnColor = if (miniBtnHovered) highlight else NanoVGColor(0.85f, 0.86f, 0.90f, alpha)
        if (isPlaying) {
            CloudMusicIconRenderer.drawPauseIcon(RenderEngine.context, 10f + miniW - 21f, miniY + 50f, 10f, miniBtnColor)
        } else {
            CloudMusicIconRenderer.drawPlayIcon(RenderEngine.context, 10f + miniW - 21f, miniY + 50f, 10f, miniBtnColor)
        }
    } else {
        NanoVGRenderer.drawCenteredString("NOCTURNE AUDIO", 10f + miniW * 0.5f, miniY + 26f,
            NanoVGColor(0.68f, 0.70f, 0.75f, alpha), 8.5f)
        NanoVGRenderer.drawCenteredString("Ready to stream", 10f + miniW * 0.5f, miniY + 42f,
            NanoVGColor(0.45f, 0.47f, 0.52f, alpha), 7.5f)
    }
}

internal fun CloudMusicScreen.renderList(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float) {
    val listX = CloudMusicScreen.LIST_X
    val listW = CloudMusicScreen.LIST_W
    val contentTop = CloudMusicScreen.CONTENT_TOP
    val contentBottom = CloudMusicScreen.CONTENT_BOTTOM
    val rowH = CloudMusicScreen.ROW_H
    val rowStep = CloudMusicScreen.ROW_STEP

    val isSearchingView = search.isNotBlank()
    val items = when {
        isSearchingView -> searchResults
        viewingPlaylist != null -> viewingPlaylistTracks
        selectedTab == CloudMusicScreen.MusicTab.DISCOVER -> CloudMusicManager.dailySongs
        selectedTab == CloudMusicScreen.MusicTab.FM -> CloudMusicManager.player.playlist
        selectedTab == CloudMusicScreen.MusicTab.FAVORITES -> CloudMusicManager.dailySongs
        else -> emptyList()
    }

    val titleText = when {
        isSearchingView -> "SEARCH RESULTS"
        viewingPlaylist != null -> "← ${viewingPlaylist!!.name.uppercase()}"
        selectedTab == CloudMusicScreen.MusicTab.DISCOVER -> "DAILY RECOMMENDATIONS"
        selectedTab == CloudMusicScreen.MusicTab.FM -> "PERSONAL FM"
        selectedTab == CloudMusicScreen.MusicTab.PLAYLISTS -> "MY PLAYLISTS"
        selectedTab == CloudMusicScreen.MusicTab.CHARTS -> "TOP RANKINGS"
        selectedTab == CloudMusicScreen.MusicTab.FAVORITES -> "MY FAVORITES"
        else -> "TRACKS"
    }

    val titleHovered = viewingPlaylist != null && inside(mx, my, listX, 80f, 180f, 18f)
    NanoVGRenderer.drawString(
        titleText, listX, 87f,
        if (titleHovered) highlight.withAlpha(alpha) else NanoVGColor(0.88f, 0.89f, 0.92f, alpha), 10f
    )

    val countText = if (selectedTab == CloudMusicScreen.MusicTab.PLAYLISTS && viewingPlaylist == null) {
        "${CloudMusicManager.userPlaylists.size} PLAYLISTS"
    } else if (selectedTab == CloudMusicScreen.MusicTab.CHARTS && viewingPlaylist == null) {
        "${CloudMusicManager.topCharts.size} CHARTS"
    } else {
        "${items.size} SONGS"
    }
    NanoVGRenderer.drawRightVerticallyCenteredString(
        countText, listX + listW - 6f, 89f, NanoVGColor(0.48f, 0.49f, 0.53f, alpha), 8f
    )

    val viewportH = contentBottom - contentTop

    if (selectedTab == CloudMusicScreen.MusicTab.PLAYLISTS && viewingPlaylist == null && !isSearchingView) {
        renderPlaylistsView(mx, my, highlight, alpha, viewportH)
        return
    }

    if (selectedTab == CloudMusicScreen.MusicTab.CHARTS && viewingPlaylist == null && !isSearchingView) {
        renderChartsView(mx, my, highlight, alpha, viewportH)
        return
    }

    val maxScroll = (items.size * rowStep - 6f - viewportH).coerceAtLeast(0f)
    listScrollTarget = listScrollTarget.coerceIn(0f, maxScroll)
    listScroll += (listScrollTarget - listScroll) * 0.30f
    if (abs(listScrollTarget - listScroll) < 0.08f) listScroll = listScrollTarget

    val currentTrack = CloudMusicManager.player.currentTrack

    NanoVGRenderer.withScissor(listX, contentTop, listW, viewportH) {
        for ((index, track) in items.withIndex()) {
            val entry = rowEntryProgress(index)
            val y = rowY(index)
            if (y + rowH < contentTop || y > contentBottom) continue

            val hovered = inside(mx, my, listX, y, listW, rowH)
            val isSelected = selectedTrack?.id == track.id
            val isPlayingCurrent = currentTrack?.id == track.id

            val visualX = listX + (1f - entry) * 8f - (if (hovered) 1.5f else 0f)
            val visualW = listW - (1f - entry) * 8f
            val rowAlpha = alpha * entry

            val rowBg = NanoVGColor(
                1f, 1f, 1f,
                (0.035f + (if (hovered) 0.035f else 0f) + (if (isSelected) 0.055f else 0f) + (if (isPlayingCurrent) 0.045f else 0f)) * rowAlpha
            )
            NanoVGRenderer.drawRoundedRect(visualX, y, visualW, rowH, 7f, rowBg)
            NanoVGRenderer.drawRoundedOutline(
                visualX, y, visualW, rowH, 7f,
                if (isPlayingCurrent) highlight.withAlpha(0.32f * rowAlpha)
                else NanoVGColor(1f, 1f, 1f, (0.055f + (if (isSelected) 0.15f else 0f)) * rowAlpha), 0.8f
            )

            val thumbX = visualX + 6.5f
            val thumbY = y + 6.5f
            CloudMusicImageCache.drawCover(track.picUrl, thumbX, thumbY, 30f, 30f, radius = 5f, alpha = rowAlpha)

            if (isPlayingCurrent) {
                NanoVGRenderer.drawRoundedRect(thumbX, thumbY, 30f, 30f, 5f,
                    NanoVGColor(0f, 0f, 0f, 0.45f * rowAlpha))
                val isPlaying = CloudMusicManager.player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING
                if (isPlaying) {
                    val time = System.currentTimeMillis() * 0.008f
                    for (b in 0..2) {
                        val h = sin(time * 2f + b * 1.4f).coerceAtLeast(0f) * 10f + 3f
                        NanoVGRenderer.drawRoundedRect(thumbX + 7f + b * 6f, thumbY + 23f - h, 2.5f, h, 1f,
                            highlight.withAlpha(rowAlpha))
                    }
                } else {
                    CloudMusicIconRenderer.drawPauseIcon(RenderEngine.context, thumbX + 15f, thumbY + 15f, 11f, highlight.withAlpha(rowAlpha))
                }
            }

            val titleColor = if (isPlayingCurrent) highlight.withAlpha(rowAlpha)
            else NanoVGColor(0.78f, 0.79f, 0.82f, rowAlpha).lerp(NanoVGColor(0.97f, 0.98f, 1f, rowAlpha), if (isSelected) 1f else 0f)

            NanoVGRenderer.drawMarqueeString(
                track.name, visualX + 42f, y + 14f, visualW - 88f,
                hovered, titleColor, 10f
            )

            val subColor = NanoVGColor(0.50f, 0.52f, 0.56f, rowAlpha)
            NanoVGRenderer.drawVerticallyCenteredString(
                track.artists, visualX + 42f, y + 29f, subColor, 8.5f
            )
            if (hovered && !isPlayingCurrent) {
                val playBtnX = visualX + visualW - 18f
                val playBtnY = y + rowH * 0.5f
                val playIconHover = inside(mx, my, playBtnX - 12f, playBtnY - 12f, 24f, 24f)
                NanoVGRenderer.drawCircle(playBtnX, playBtnY, 10.5f,
                    if (playIconHover) highlight.withAlpha(0.20f * rowAlpha)
                    else NanoVGColor(1f, 1f, 1f, 0.08f * rowAlpha))

                val playColor = if (playIconHover) highlight else NanoVGColor(0.88f, 0.89f, 0.94f, rowAlpha)
                CloudMusicIconRenderer.drawPlayIcon(
                    RenderEngine.context, playBtnX + 0.5f, playBtnY, 9.5f, playColor
                )
            } else {
                NanoVGRenderer.drawRightVerticallyCenteredString(
                    track.durationFormatted, visualX + visualW - 10f, y + 21.5f, subColor, 8f
                )
            }
        }
    }

    if (items.isEmpty()) {
        val emptyMsg = when {
            isSearching -> "Searching songs..."
            isSearchingView -> "No songs found for '$search'"
            selectedTab == CloudMusicScreen.MusicTab.DISCOVER && CloudMusicManager.dailySongs.isEmpty() -> "Loading daily songs..."
            else -> "No songs available"
        }
        NanoVGRenderer.drawCenteredString(emptyMsg, listX + listW * 0.5f, 247f,
            NanoVGColor(0.73f, 0.74f, 0.78f, alpha), 10.5f)
        NanoVGRenderer.drawCenteredString("Log in or choose another tab", listX + listW * 0.5f, 268f,
            NanoVGColor(0.47f, 0.48f, 0.52f, alpha), 9f)
    }

    drawScrollBar(listX + listW - 2f, contentTop, viewportH, items.size * rowStep - 6f, listScroll, alpha)
}

internal fun CloudMusicScreen.renderPlaylistsView(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float, viewportH: Float) {
    val listX = CloudMusicScreen.LIST_X
    val listW = CloudMusicScreen.LIST_W
    val contentTop = CloudMusicScreen.CONTENT_TOP
    val contentBottom = CloudMusicScreen.CONTENT_BOTTOM
    val rowH = CloudMusicScreen.ROW_H
    val rowStep = CloudMusicScreen.ROW_STEP

    val user = CloudMusicManager.api.currentUser
    if (user == null) {
        NanoVGRenderer.drawCenteredString("Please sign in to NetEase Music", listX + listW * 0.5f, 235f,
            NanoVGColor(0.85f, 0.86f, 0.90f, alpha), 11f)
        NanoVGRenderer.drawCenteredString("View your private playlists & favorites", listX + listW * 0.5f, 255f,
            NanoVGColor(0.50f, 0.52f, 0.56f, alpha), 9f)

        val btnHovered = inside(mx, my, listX + 60f, 280f, listW - 120f, 28f)
        NanoVGRenderer.drawRoundedRect(listX + 60f, 280f, listW - 120f, 28f, 6f,
            if (btnHovered) highlight.withAlpha(0.18f * alpha) else NanoVGColor(1f, 1f, 1f, 0.08f * alpha))
        NanoVGRenderer.drawRoundedOutline(listX + 60f, 280f, listW - 120f, 28f, 6f,
            highlight.withAlpha(0.25f * alpha), 1f)
        NanoVGRenderer.drawCenteredString("Sign In via QR", listX + listW * 0.5f, 294f,
            NanoVGColor.WHITE.withAlpha(alpha), 9.5f)
        return
    }

    val playlists = CloudMusicManager.userPlaylists
    val maxScroll = (playlists.size * rowStep - 6f - viewportH).coerceAtLeast(0f)
    listScrollTarget = listScrollTarget.coerceIn(0f, maxScroll)
    listScroll += (listScrollTarget - listScroll) * 0.30f

    NanoVGRenderer.withScissor(listX, contentTop, listW, viewportH) {
        for ((index, pl) in playlists.withIndex()) {
            val entry = rowEntryProgress(index)
            val y = rowY(index)
            if (y + rowH < contentTop || y > contentBottom) continue

            val hovered = inside(mx, my, listX, y, listW, rowH)
            val visualX = listX + (1f - entry) * 8f - (if (hovered) 1.5f else 0f)
            val visualW = listW - (1f - entry) * 8f
            val rowAlpha = alpha * entry

            NanoVGRenderer.drawRoundedRect(visualX, y, visualW, rowH, 7f,
                NanoVGColor(1f, 1f, 1f, (0.035f + (if (hovered) 0.045f else 0f)) * rowAlpha))
            NanoVGRenderer.drawRoundedOutline(visualX, y, visualW, rowH, 7f,
                NanoVGColor(1f, 1f, 1f, (0.055f + (if (hovered) 0.12f else 0f)) * rowAlpha), 0.8f)

            CloudMusicImageCache.drawCover(pl.coverUrl, visualX + 6.5f, y + 6.5f, 30f, 30f, radius = 5f, alpha = rowAlpha)

            NanoVGRenderer.drawMarqueeString(
                pl.name, visualX + 42f, y + 14f, visualW - 55f,
                hovered, NanoVGColor(0.85f, 0.86f, 0.90f, rowAlpha), 10f
            )
            NanoVGRenderer.drawVerticallyCenteredString(
                "${pl.trackCount} Tracks · ${pl.playCountFormatted} Plays", visualX + 42f, y + 29f,
                NanoVGColor(0.50f, 0.52f, 0.56f, rowAlpha), 8.5f
            )
        }
    }
    drawScrollBar(listX + listW - 2f, contentTop, viewportH, playlists.size * rowStep - 6f, listScroll, alpha)
}

internal fun CloudMusicScreen.renderChartsView(mx: Float, my: Float, highlight: NanoVGColor, alpha: Float, viewportH: Float) {
    val listX = CloudMusicScreen.LIST_X
    val listW = CloudMusicScreen.LIST_W
    val contentTop = CloudMusicScreen.CONTENT_TOP
    val contentBottom = CloudMusicScreen.CONTENT_BOTTOM
    val rowH = CloudMusicScreen.ROW_H
    val rowStep = CloudMusicScreen.ROW_STEP

    val charts = CloudMusicManager.topCharts
    val maxScroll = (charts.size * rowStep - 6f - viewportH).coerceAtLeast(0f)
    listScrollTarget = listScrollTarget.coerceIn(0f, maxScroll)
    listScroll += (listScrollTarget - listScroll) * 0.30f

    NanoVGRenderer.withScissor(listX, contentTop, listW, viewportH) {
        for ((index, chart) in charts.withIndex()) {
            val entry = rowEntryProgress(index)
            val y = rowY(index)
            if (y + rowH < contentTop || y > contentBottom) continue

            val hovered = inside(mx, my, listX, y, listW, rowH)
            val visualX = listX + (1f - entry) * 8f - (if (hovered) 1.5f else 0f)
            val visualW = listW - (1f - entry) * 8f
            val rowAlpha = alpha * entry

            NanoVGRenderer.drawRoundedRect(visualX, y, visualW, rowH, 7f,
                NanoVGColor(1f, 1f, 1f, (0.035f + (if (hovered) 0.045f else 0f)) * rowAlpha))
            NanoVGRenderer.drawRoundedOutline(visualX, y, visualW, rowH, 7f,
                NanoVGColor(1f, 1f, 1f, (0.055f + (if (hovered) 0.12f else 0f)) * rowAlpha), 0.8f)

            CloudMusicImageCache.drawCover(chart.coverUrl, visualX + 6.5f, y + 6.5f, 30f, 30f, radius = 5f, alpha = rowAlpha)

            NanoVGRenderer.drawMarqueeString(
                chart.name, visualX + 42f, y + 14f, visualW - 55f,
                hovered, NanoVGColor(0.88f, 0.89f, 0.94f, rowAlpha), 10f
            )
            NanoVGRenderer.drawVerticallyCenteredString(
                "${chart.trackCount} Tracks · Official Chart", visualX + 42f, y + 29f,
                NanoVGColor(0.50f, 0.52f, 0.56f, rowAlpha), 8.5f
            )
        }
    }
    drawScrollBar(listX + listW - 2f, contentTop, viewportH, charts.size * rowStep - 6f, listScroll, alpha)
}

internal fun CloudMusicScreen.renderMiniVinyl(cx: Float, cy: Float, radius: Float, picUrl: String, isPlaying: Boolean, alpha: Float) {
    val nvg = RenderEngine.nanoVG.handle
    NanoVG.nvgSave(nvg)
    NanoVG.nvgTranslate(nvg, cx, cy)
    if (isPlaying) NanoVG.nvgRotate(nvg, vinylAngle)

    NanoVGRenderer.drawCircle(0f, 0f, radius, NanoVGColor(0.08f, 0.08f, 0.10f, 0.95f * alpha))
    NanoVGRenderer.drawCircle(0f, 0f, radius * 0.85f, NanoVGColor(0.12f, 0.12f, 0.14f, 0.40f * alpha))

    CloudMusicImageCache.drawCover(picUrl, -radius * 0.55f, -radius * 0.55f, radius * 1.1f, radius * 1.1f,
        radius = radius * 0.55f, alpha = alpha)
    NanoVGRenderer.drawCircle(0f, 0f, 2f, NanoVGColor(0.05f, 0.05f, 0.07f, alpha))

    NanoVG.nvgRestore(nvg)
}

internal fun CloudMusicScreen.drawScrollBar(x: Float, y: Float, viewportH: Float, contentH: Float, scroll: Float, alpha: Float) {
    if (contentH <= viewportH) return
    val thumbH = (viewportH * viewportH / contentH).coerceAtLeast(22f)
    val thumbY = y + (scroll / (contentH - viewportH)) * (viewportH - thumbH)
    NanoVGRenderer.drawRoundedRect(x, y, 2f, viewportH, 1f, NanoVGColor(1f, 1f, 1f, 0.07f * alpha))
    NanoVGRenderer.drawRoundedRect(x, thumbY, 2f, thumbH, 1f, NanoVGColor(0.78f, 0.79f, 0.82f, 0.52f * alpha))
}
