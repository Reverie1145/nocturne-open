package cn.reverie.app.music.ui

import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.api.*
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.render.animation.CubicBezier
import cn.reverie.render.animation.FloatAnimation
import cn.reverie.render.animation.MotionBlurHelper
import cn.reverie.render.animation.MotionTracker
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.ui.NvgScreen
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.concurrent.thread

/**
 * Nocturne CloudMusic Window GUI
 * Strictly adheres to Nocturne's ClickGUI (WindowClickGuiScreen) design language:
 * - 790x490 movable glass workspace with real-time backdrop blur & motion blur trails
 * - Sleek ClickGUI header with search animation, account pill, and close button
 * - Identical 148px sidebar with gliding category pills and project resource icons
 * - Card list & Right Detail stage with synced lyrics, vinyl art, and audio controls
 */
class CloudMusicScreen(val parent: Screen? = null) :
    Screen(Component.literal("Nocturne Music")), NvgScreen {

    enum class MusicTab(val title: String, val iconName: String) {
        DISCOVER("Discover", "scripts.png"),
        FM("Private FM", "misc.png"),
        PLAYLISTS("Playlists", "player.png"),
        CHARTS("Charts", "combat.png"),
        FAVORITES("Favorites", "render.png")
    }

    internal data class WindowFrame(val x: Float, val y: Float, val scale: Float)

    companion object {
        internal const val W = 790f
        internal const val H = 490f
        internal const val SIDEBAR_W = 148f
        internal const val LIST_X = 164f
        internal const val LIST_W = 254f
        internal const val DETAIL_X = 432f
        internal const val DETAIL_W = 342f
        internal const val CONTENT_TOP = 108f
        internal const val CONTENT_BOTTOM = 470f
        internal const val SIDEBAR_Y = 82f
        internal const val SIDEBAR_ROW_H = 29f
        internal const val SIDEBAR_ROW_STEP = 32f
        internal const val ROW_H = 43f
        internal const val ROW_STEP = 49f

        internal var lastTab: MusicTab = MusicTab.DISCOVER
        internal var lastListScroll = 0f
    }

    // ==========================================
    // Animations & Motions
    // ==========================================
    internal val openAnim = FloatAnimation(0f, 1f, 360L, CubicBezier.EASE_OUT_BACK)
    internal val searchFocusAnim = FloatAnimation(0f, 0f, 190L, CubicBezier.EASE_OUT)
    internal val closeHoverAnim = FloatAnimation(0f, 0f, 130L, CubicBezier.EASE_OUT)
    internal val listTransitionAnim = FloatAnimation(1f, 1f, 260L, CubicBezier.EASE_OUT)
    internal val detailTransitionAnim = FloatAnimation(1f, 1f, 240L, CubicBezier.EASE_OUT)
    internal val qrModalAnim = FloatAnimation(0f, 0f, 240L, CubicBezier.EASE_OUT_BACK)
    internal val playBtnHoverAnim = FloatAnimation(0f, 0f, 150L, CubicBezier.EASE_OUT)

    internal val tabHoverMotions = MusicTab.entries.associateWith { FloatAnimation(0f, 0f, 160L, CubicBezier.EASE_OUT) }
    internal val tabSelectMotions = MusicTab.entries.associateWith {
        FloatAnimation(if (it == lastTab) 1f else 0f, if (it == lastTab) 1f else 0f, 220L, CubicBezier.EASE_OUT)
    }

    // Dragging & Motion Blur
    internal val motionTracker = MotionTracker()
    internal var dragOffsetX = 0f
    internal var dragOffsetY = 0f
    internal var dragging = false
    internal var dragStartX = 0f
    internal var dragStartY = 0f
    internal var isClosing = false

    // Tabs & Navigation State
    internal var selectedTab = lastTab
    internal var selectedTrack: MusicTrack? = null
    internal var sharePickerOpen = false
    internal var sharePickerScroll = 0
    internal var viewingPlaylist: MusicPlaylist? = null
    internal var viewingPlaylistTracks = listOf<MusicTrack>()
    internal var isLoadingPlaylist = false

    // Search
    internal var search = ""
    internal var searchFocused = false
    internal var searchResults = listOf<MusicTrack>()
    internal var isSearching = false

    // Scrolling
    internal var listScroll = 0f
    internal var listScrollTarget = 0f
    internal var lyricScroll = 0f

    // Progress Scrubbing & Volume Dragging
    internal var isDraggingProgress = false
    internal var dragProgressMs = 0L
    internal var isDraggingVolume = false

    // QR Login Modal State
    internal var showQRModal = false
    internal var qrKey: String? = null
    internal var qrBytes: ByteArray? = null
    internal var qrStatus = QRStatus.WAITING
    internal var qrStatusMsg = "Scan QR code with NetEase Music App"
    internal var qrSuccessTimestamp = 0L

    // Vinyl Disc Rotation & Inertia
    internal var vinylAngle = 0f
    internal var vinylSpeed = 0f
    internal val heartPopAnim = FloatAnimation(1f, 1f, 260L, CubicBezier.EASE_OUT_BACK)

    init {
        selectedTab = lastTab
        listScroll = lastListScroll
        listScrollTarget = lastListScroll
        tabSelectMotions.forEach { (tab, anim) -> anim.setImmediate(if (tab == selectedTab) 1f else 0f) }
        selectedTrack = CloudMusicManager.player.currentTrack
    }

    override fun init() {
        super.init()
        if (!isClosing) {
            openAnim.setImmediate(0f)
            openAnim.animateTo(1f)
            listTransitionAnim.setImmediate(0f)
            listTransitionAnim.animateTo(1f)
            detailTransitionAnim.setImmediate(0f)
            detailTransitionAnim.animateTo(1f)
        }
        CloudMusicManager.refreshHomeData()
    }

    override fun tick() {
        super.tick()
        val isPlaying = CloudMusicManager.player.playbackState == CloudMusicPlayer.PlaybackState.PLAYING
        val targetSpeed = if (isPlaying) 0.034f else 0f
        vinylSpeed += (targetSpeed - vinylSpeed) * 0.08f
        vinylAngle += vinylSpeed
        if (vinylAngle > Math.PI * 2) vinylAngle -= (Math.PI * 2).toFloat()

        // Auto close QR modal on success after delay
        if (showQRModal && qrStatus == QRStatus.SUCCESS && qrSuccessTimestamp > 0L) {
            if (System.currentTimeMillis() - qrSuccessTimestamp > 1400L) {
                closeQRModal()
            }
        }
    }

    fun startClose() {
        if (isClosing) return
        isClosing = true
        searchFocused = false
        dragging = false
        isDraggingProgress = false
        isDraggingVolume = false
        lastTab = selectedTab
        lastListScroll = listScrollTarget
        openAnim.durationMs = 180L
        openAnim.animateTo(0f)
    }

    override fun onClose() = startClose()
    override fun isPauseScreen(): Boolean = false

    override fun removed() {
        super.removed()
        lastTab = selectedTab
        lastListScroll = listScrollTarget
        CloudMusicManager.stopQRLogin()
    }

    internal fun frame(): WindowFrame {
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val baseScale = minOf(1f, (screenW - 20f) / W, (screenH - 20f) / H).coerceAtLeast(0.25f)
        val scale = baseScale * (0.94f + openAnim.value * 0.06f)
        val actualW = W * scale
        val actualH = H * scale
        val x = ((screenW - actualW) * 0.5f + dragOffsetX).coerceIn(0f, (screenW - actualW).coerceAtLeast(0f))
        val y = ((screenH - actualH) * 0.5f + dragOffsetY).coerceIn(0f, (screenH - actualH).coerceAtLeast(0f))
        return WindowFrame(x, y, scale)
    }

    internal fun localX(x: Float, frame: WindowFrame) = (x - frame.x) / frame.scale
    internal fun localY(y: Float, frame: WindowFrame) = (y - frame.y) / frame.scale
    internal fun inside(x: Float, y: Float, rx: Float, ry: Float, rw: Float, rh: Float) =
        x >= rx && x <= rx + rw && y >= ry && y <= ry + rh

    internal fun restartListTransition() {
        listTransitionAnim.setImmediate(0f)
        listTransitionAnim.durationMs = 260L
        listTransitionAnim.animateTo(1f)
    }

    internal fun rowEntryProgress(index: Int): Float {
        val staggered = ((listTransitionAnim.value * 1.45f) - index * 0.028f).coerceIn(0f, 1f)
        return 1f - (1f - staggered) * (1f - staggered) * (1f - staggered)
    }

    internal fun rowY(index: Int): Float = CONTENT_TOP + index * ROW_STEP - listScroll + (1f - rowEntryProgress(index)) * 13f

    // ==========================================
    // Render Loop (NanoVG)
    // ==========================================
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor) {}
    override fun extractMenuBackground(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {}
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {}

    override fun renderNvg(mouseX: Float, mouseY: Float, partialTicks: Float) {
        if (isClosing && (openAnim.isFinished || openAnim.value <= 0.02f)) {
            Minecraft.getInstance().gui.setScreen(parent)
            return
        }

        val alpha = openAnim.value.coerceIn(0f, 1f)
        val screenW = ResolutionHelper.scaledWidth
        val screenH = ResolutionHelper.scaledHeight
        val frame = frame()
        val mx = localX(mouseX, frame)
        val my = localY(mouseY, frame)
        val highlight = NanoVGColor(0.91f, 0.92f, 0.94f, 1f)

        // 1. Fullscreen dark backdrop overlay
        NanoVGRenderer.drawRoundedRect(0f, 0f, screenW, screenH, 0f,
            NanoVGColor(0f, 0f, 0f, 0.35f * alpha))

        // 2. Window Drop Shadow
        if (HUDModule.widgetShadow.value) {
            Draw2D.shadow(
                frame.x, frame.y + 4f, W * frame.scale, H * frame.scale,
                14f * frame.scale, 24f * frame.scale,
                NanoVGColor(0f, 0f, 0f, 0.47f * alpha * HUDModule.shadowStrength.getFloat())
            )
        }

        // 3. Real-time Glass Backdrop Blur
        try {
            RenderEngine.backdropBlurRenderer.renderBackdrop(
                frame.x, frame.y, W * frame.scale, H * frame.scale,
                radius = 13f * frame.scale,
                tintColor = NanoVGColor(0.047f, 0.051f, 0.067f, HUDModule.bgOpacity.getFloat() * alpha),
                blurRadius = HUDModule.blurRadius.getFloat() * alpha,
                enableBlur = HUDModule.blur.value,
                backdropOpacity = HUDModule.bgOpacity.getFloat() * alpha
            )
        } catch (_: Throwable) {
            NanoVGRenderer.drawRoundedRect(
                frame.x, frame.y, W * frame.scale, H * frame.scale,
                13f * frame.scale, NanoVGColor(0.047f, 0.051f, 0.067f, HUDModule.bgOpacity.getFloat() * alpha)
            )
        }

        // 4. Motion Blur while dragging window
        motionTracker.update(frame.x, frame.y)
        if (dragging && motionTracker.isMoving) {
            MotionBlurHelper.renderMotionBlur(
                frame.x, frame.y, W * frame.scale, H * frame.scale,
                13f * frame.scale, motionTracker.vx, motionTracker.vy,
                color = NanoVGColor(1f, 1f, 1f, 0.22f * alpha)
            )
        }

        // 5. Render Window Contents via NanoVG Transform
        val transform = RenderEngine.nanoVG.transform
        transform.save()
        try {
            transform.translate(frame.x, frame.y)
            transform.scale(frame.scale, frame.scale)

            // Outer border outline
            NanoVGRenderer.drawRoundedOutline(0f, 0f, W, H, 13f, NanoVGColor(1f, 1f, 1f, 0.12f * alpha), 1f)
            // Sidebar darker glass fill
            NanoVGRenderer.drawRoundedRect(1f, 62f, SIDEBAR_W - 1f, H - 63f, 0f, NanoVGColor(0f, 0f, 0f, 0.12f * alpha))
            // Header divider line
            Draw2D.line(0f, 61f, W, 61f, NanoVGColor(1f, 1f, 1f, 0.08f * alpha), 1f)

            // Render sub-components across separate renderers
            renderHeader(mx, my, highlight, alpha)
            renderSidebar(mx, my, highlight, alpha)
            renderList(mx, my, highlight, alpha)
            renderDetails(mx, my, partialTicks, highlight, alpha)

            // QR Login Modal Dialog Overlay
            if (showQRModal || qrModalAnim.value > 0.01f) {
                renderQRModal(mx, my, alpha)
            }
        } finally {
            transform.restore()
        }
    }

    // ==========================================
    // Mouse & Keyboard Inputs (ClickGUI Style)
    // ==========================================
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val frame = frame()
        val canvasX = ResolutionHelper.toCanvasX(event.x().toFloat())
        val canvasY = ResolutionHelper.toCanvasY(event.y().toFloat())
        val mx = localX(canvasX, frame)
        val my = localY(canvasY, frame)
        val button = event.button()

        if (!inside(mx, my, 0f, 0f, W, H)) {
            searchFocused = false
            return true
        }

        // 1. QR Modal Interactions
        if (showQRModal) {
            val cardW = 260f
            val cardH = 290f
            val cardX = (W - cardW) * 0.5f
            val cardY = (H - cardH) * 0.5f

            if (button == 0) {
                if (inside(mx, my, cardX + cardW - 32f, cardY + 12f, 22f, 22f) || !inside(mx, my, cardX, cardY, cardW, cardH)) {
                    closeQRModal()
                    return true
                }
                if ((qrStatus == QRStatus.EXPIRED || qrStatus == QRStatus.ERROR) && inside(mx, my, cardX + 60f, cardY + 246f, cardW - 120f, 26f)) {
                    openQRModal()
                    return true
                }
            }
            return true
        }

        // 2. Header Interactions
        if (button == 0) {
            if (inside(mx, my, 755f, 17f, 22f, 27f)) {
                startClose()
                return true
            }

            if (inside(mx, my, 595f, 17f, 150f, 27f)) {
                openQRModal()
                return true
            }

            val searchW = 330f + 12f * searchFocusAnim.value
            if (inside(mx, my, 183f, 16f, searchW, 29f)) {
                if (search.isNotEmpty() && mx >= 183f + searchW - 24f) {
                    search = ""
                    listScrollTarget = 0f
                    restartListTransition()
                } else {
                    searchFocused = true
                }
                return true
            }
            searchFocused = false

            if (inside(mx, my, 0f, 0f, W, 61f)) {
                dragging = true
                dragStartX = canvasX - frame.x
                dragStartY = canvasY - frame.y
                return true
            }

            var tabY = SIDEBAR_Y
            for (tab in MusicTab.entries) {
                if (inside(mx, my, 10f, tabY, 129f, SIDEBAR_ROW_H)) {
                    selectedTab = tab
                    search = ""
                    viewingPlaylist = null
                    listScrollTarget = 0f
                    restartListTransition()

                    if (tab == MusicTab.FM && !CloudMusicManager.player.isFmMode) {
                        thread(name = "CloudMusic-Start-FM", isDaemon = true) {
                            val fmTracks = CloudMusicManager.api.fetchPersonalFm()
                            if (fmTracks.isNotEmpty()) {
                                CloudMusicManager.player.playFm(fmTracks)
                            }
                        }
                    }
                    return true
                }
                tabY += SIDEBAR_ROW_STEP
            }

            if (inside(mx, my, 10f + 129f - 32f, 405f + 40f, 22f, 20f)) {
                CloudMusicManager.player.togglePlay()
                return true
            }
        }

        // 3. Left List Interactions
        if (inside(mx, my, LIST_X, 75f, LIST_W, CONTENT_BOTTOM - 75f)) {
            if (viewingPlaylist != null && inside(mx, my, LIST_X, 80f, 180f, 20f) && button == 0) {
                viewingPlaylist = null
                listScrollTarget = 0f
                restartListTransition()
                return true
            }

            if (selectedTab == MusicTab.PLAYLISTS && viewingPlaylist == null && search.isEmpty()) {
                val playlists = CloudMusicManager.userPlaylists
                for ((index, pl) in playlists.withIndex()) {
                    val y = rowY(index)
                    if (inside(mx, my, LIST_X, y, LIST_W, ROW_H) && button == 0) {
                        viewingPlaylist = pl
                        isLoadingPlaylist = true
                        listScrollTarget = 0f
                        restartListTransition()
                        thread(name = "CloudMusic-Load-Playlist", isDaemon = true) {
                            try {
                                val (_, tracks) = CloudMusicManager.api.fetchPlaylistDetail(pl.id)
                                viewingPlaylistTracks = tracks
                            } finally {
                                isLoadingPlaylist = false
                            }
                        }
                        return true
                    }
                }
                return true
            }

            if (selectedTab == MusicTab.CHARTS && viewingPlaylist == null && search.isEmpty()) {
                val charts = CloudMusicManager.topCharts
                for ((index, chart) in charts.withIndex()) {
                    val y = rowY(index)
                    if (inside(mx, my, LIST_X, y, LIST_W, ROW_H) && button == 0) {
                        viewingPlaylist = chart
                        isLoadingPlaylist = true
                        listScrollTarget = 0f
                        restartListTransition()
                        thread(name = "CloudMusic-Load-Chart", isDaemon = true) {
                            try {
                                val (_, tracks) = CloudMusicManager.api.fetchPlaylistDetail(chart.id)
                                viewingPlaylistTracks = tracks
                            } finally {
                                isLoadingPlaylist = false
                            }
                        }
                        return true
                    }
                }
                return true
            }

            val items = when {
                search.isNotBlank() -> searchResults
                viewingPlaylist != null -> viewingPlaylistTracks
                selectedTab == MusicTab.DISCOVER -> CloudMusicManager.dailySongs
                selectedTab == MusicTab.FM -> CloudMusicManager.player.playlist
                selectedTab == MusicTab.FAVORITES -> CloudMusicManager.dailySongs
                else -> emptyList()
            }

            for ((index, track) in items.withIndex()) {
                val y = rowY(index)
                if (!inside(mx, my, LIST_X, y, LIST_W, ROW_H)) continue

                if (button == 0) {
                    selectedTrack = track
                    if (doubleClick || inside(mx, my, LIST_X + LIST_W - 32f, y + 8f, 24f, 24f)) {
                        CloudMusicManager.player.playPlaylist(items, index)
                    }
                    return true
                }
            }
            return true
        }

        // 4. Right Detail Panel Interactions
        if (inside(mx, my, DETAIL_X, 77f, DETAIL_W, 393f)) {
            val track = selectedTrack ?: CloudMusicManager.player.currentTrack

            if (track != null && inside(mx, my, DETAIL_X + 116f, 154f, 64f, 16f) && button == 0) {
                sharePickerOpen = !sharePickerOpen
                return true
            }

            if (sharePickerOpen) {
                val contacts = cn.reverie.gui.clickgui.irc.IrcChatModel.contacts().take(6)
                val panelX = DETAIL_X + 16f
                val panelY = 194f
                val panelW = DETAIL_W - 32f
                val panelH = 172f
                if (button == 0 && inside(mx, my, panelX, panelY, panelW, panelH)) {
                    val index = ((my - (panelY + 25f)) / 23f).toInt()
                    if (my >= panelY + 25f) contacts.getOrNull(index)?.let { target ->
                        cn.reverie.nocturne.IrcShareManager.shareMusic(target, track)
                        sharePickerOpen = false
                    }
                    return true
                }
                sharePickerOpen = false
            }

            if (track != null && inside(mx, my, DETAIL_X + DETAIL_W - 30f, 150f, 24f, 24f) && button == 0) {
                CloudMusicManager.toggleLikeSong(track)
                heartPopAnim.setImmediate(1.45f)
                heartPopAnim.animateTo(1.0f)
                return true
            }

            val btnY = 432f
            if (inside(mx, my, DETAIL_X + 18f, 376f, DETAIL_W - 36f, 18f) && button == 0) {
                isDraggingProgress = true
                val ratio = ((mx - (DETAIL_X + 18f)) / (DETAIL_W - 36f)).coerceIn(0f, 1f)
                dragProgressMs = (ratio * CloudMusicManager.player.durationMs).toLong()
                return true
            }

            // Play / Pause central button
            if (inside(mx, my, DETAIL_X + 107f, btnY - 17f, 34f, 34f) && button == 0) {
                CloudMusicManager.player.togglePlay()
                return true
            }

            // Previous track
            if (inside(mx, my, DETAIL_X + 62f, btnY - 14f, 28f, 28f) && button == 0) {
                CloudMusicManager.player.previous()
                return true
            }

            // Next track
            if (inside(mx, my, DETAIL_X + 158f, btnY - 14f, 28f, 28f) && button == 0) {
                CloudMusicManager.player.next()
                return true
            }

            // Cycle play mode
            if (inside(mx, my, DETAIL_X + 18f, btnY - 14f, 28f, 28f) && button == 0) {
                CloudMusicManager.player.cyclePlayMode()
                return true
            }

            // Speaker mute toggle
            if (inside(mx, my, DETAIL_X + 209f, btnY - 11f, 22f, 22f) && button == 0) {
                val currentVol = CloudMusicManager.player.volume
                if (currentVol > 0.001f) {
                    CloudMusicManager.player.volume = 0f
                } else {
                    CloudMusicManager.player.volume = 0.8f
                }
                return true
            }

            // Volume slider rail
            if (inside(mx, my, DETAIL_X + 234f, btnY - 8f, 82f, 16f) && button == 0) {
                isDraggingVolume = true
                val vol = ((mx - (DETAIL_X + 238f)) / 74f).coerceIn(0f, 1f)
                CloudMusicManager.player.volume = vol
                return true
            }

            return true
        }

        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == 0) {
            dragging = false
            if (isDraggingProgress) {
                isDraggingProgress = false
                CloudMusicManager.player.seekTo(dragProgressMs)
            }
            isDraggingVolume = false
        }
        return super.mouseReleased(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        val mouseX = ResolutionHelper.toCanvasX(event.x().toFloat())
        val mouseY = ResolutionHelper.toCanvasY(event.y().toFloat())
        if (dragging && event.button() == 0) {
            val screenW = ResolutionHelper.scaledWidth
            val screenH = ResolutionHelper.scaledHeight
            val scale = frame().scale
            val x = (mouseX - dragStartX).coerceIn(0f, (screenW - W * scale).coerceAtLeast(0f))
            val y = (mouseY - dragStartY).coerceIn(0f, (screenH - H * scale).coerceAtLeast(0f))
            dragOffsetX = x - (screenW - W * scale) * 0.5f
            dragOffsetY = y - (screenH - H * scale) * 0.5f
            return true
        }

        val frame = frame()
        val mx = localX(mouseX, frame)

        if (isDraggingProgress) {
            val ratio = ((mx - (DETAIL_X + 18f)) / (DETAIL_W - 36f)).coerceIn(0f, 1f)
            dragProgressMs = (ratio * CloudMusicManager.player.durationMs).toLong()
            return true
        }

        if (isDraggingVolume) {
            val vol = ((mx - (DETAIL_X + 238f)) / 74f).coerceIn(0f, 1f)
            CloudMusicManager.player.volume = vol
            return true
        }

        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseScrolled(x: Double, y: Double, scrollX: Double, scrollY: Double): Boolean {
        val frame = frame()
        val mx = localX(ResolutionHelper.toCanvasX(x).toFloat(), frame)
        val my = localY(ResolutionHelper.toCanvasY(y).toFloat(), frame)

        if (inside(mx, my, LIST_X, CONTENT_TOP, LIST_W, CONTENT_BOTTOM - CONTENT_TOP)) {
            listScrollTarget = (listScrollTarget - scrollY.toFloat() * 29f).coerceAtLeast(0f)
            return true
        }

        if (inside(mx, my, DETAIL_X, 194f, DETAIL_W, 172f)) {
            lyricScroll = (lyricScroll - scrollY.toFloat() * 24f).coerceAtLeast(0f)
            return true
        }

        return super.mouseScrolled(x, y, scrollX, scrollY)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = event.key()

        if (showQRModal) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeQRModal()
                return true
            }
            return true
        }

        if (searchFocused) {
            when (key) {
                GLFW.GLFW_KEY_ESCAPE -> { searchFocused = false; return true }
                GLFW.GLFW_KEY_ENTER -> {
                    searchFocused = false
                    executeSearch(search)
                    return true
                }
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (search.isNotEmpty()) {
                        search = search.dropLast(1)
                        if (search.isEmpty()) {
                            listScrollTarget = 0f
                            restartListTransition()
                        } else {
                            executeSearch(search)
                        }
                    }
                    return true
                }
            }
        }

        // Global Music Keyboard Shortcuts
        when (key) {
            GLFW.GLFW_KEY_SPACE -> {
                CloudMusicManager.player.togglePlay()
                return true
            }
            GLFW.GLFW_KEY_LEFT -> {
                CloudMusicManager.player.previous()
                return true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                CloudMusicManager.player.next()
                return true
            }
            GLFW.GLFW_KEY_F -> if (event.modifiers() and GLFW.GLFW_MOD_CONTROL != 0) {
                searchFocused = true
                return true
            }
            GLFW.GLFW_KEY_ESCAPE -> {
                startClose()
                return true
            }
        }

        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (searchFocused && event.codepoint > 0) {
            val c = event.codepoint.toChar()
            if (!c.isISOControl() && search.length < 60) {
                search += c
                executeSearch(search)
                return true
            }
        }
        return super.charTyped(event)
    }

    private fun executeSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        isSearching = true
        thread(name = "CloudMusic-Search", isDaemon = true) {
            try {
                val results = CloudMusicManager.api.searchTracks(q, limit = 40)
                searchResults = results
                listScrollTarget = 0f
                restartListTransition()
            } catch (e: Exception) {
                System.err.println("[CloudMusicScreen] Search error: ${e.message}")
            } finally {
                isSearching = false
            }
        }
    }

    internal fun openQRModal() {
        showQRModal = true
        qrBytes = null
        qrKey = null
        qrStatus = QRStatus.WAITING
        qrStatusMsg = "Generating QR Code..."
        qrSuccessTimestamp = 0L

        CloudMusicManager.startQRLogin(
            onQRCodeReady = { key, bytes ->
                qrKey = key
                qrBytes = bytes
            },
            onStatusUpdate = { status, msg ->
                qrStatus = status
                qrStatusMsg = msg
                if (status == QRStatus.SUCCESS) {
                    qrSuccessTimestamp = System.currentTimeMillis()
                }
            }
        )
    }

    internal fun closeQRModal() {
        showQRModal = false
        CloudMusicManager.stopQRLogin()
    }
}
