package cn.reverie.app.music

import cn.reverie.app.music.api.*
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.nocturne.IrcShareManager
import net.minecraft.client.Minecraft
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Global singleton managing CloudMusic API, playback, and session state.
 */
object CloudMusicManager {

    val api = CloudMusicApi()
    val player = CloudMusicPlayer(api)

    init {
        player.onTrackChanged = { IrcShareManager.onLocalMusicChanged(it, player.playbackState) }
        player.onStateChanged = { IrcShareManager.onLocalMusicChanged(player.currentTrack, it) }
    }

    val likedSongIds = ConcurrentHashMap.newKeySet<Long>()
    var userPlaylists = listOf<MusicPlaylist>()
        private set

    var dailySongs = listOf<MusicTrack>()
        private set

    var dailyPlaylists = listOf<MusicPlaylist>()
        private set

    var topPlaylists = listOf<MusicPlaylist>()
        private set

    val DEFAULT_CHARTS = listOf(
        MusicPlaylist(19723756L, "Soaring Chart", "https://p1.music.126.net/DrrweDMWUZ6_ZaE03q062Q==/109951167660600843.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(3779629L, "New Songs Chart", "https://p1.music.126.net/N2HO5xfYEqyvgnq5MfGC6g==/109951168055301828.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(3778678L, "Hot Songs Chart", "https://p1.music.126.net/Zy7lZz-fI_fF8i3c4a8wkg==/109951168055306692.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(2884035L, "Original Chart", "https://p1.music.126.net/sBzD11nforcuh1jdLSgX7g==/18740076185638788.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(5453912201L, "Vinyl VIP Chart", "https://p1.music.126.net/M_b7fBw3nQGvGzU4b1L39Q==/109951165609825488.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(991319590L, "Rap Chart", "https://p1.music.126.net/e_F3QxO9yM4H-pXj19zXwQ==/109951165609829788.jpg", 100, 0, "NetEase Cloud Music", 1),
        MusicPlaylist(1978921795L, "Electronic Chart", "https://p1.music.126.net/6yU_rE1L1r3E_lU7H8d8Yg==/109951165609833588.jpg", 100, 0, "NetEase Cloud Music", 1)
    )

    var topCharts = DEFAULT_CHARTS
        private set

    private val isFetchingHome = AtomicBoolean(false)
    private var qrPollingThread: Thread? = null
    private val qrPollingActive = AtomicBoolean(false)

    init {
        // Initial auto-refresh if cookies exist
        if (api.cookies.isNotBlank()) {
            thread(name = "CloudMusic-Init-Account", isDaemon = true) {
                val user = api.refreshAccount()
                if (user != null) {
                    loadUserData()
                }
            }
        }

        // Preload home data asynchronously
        refreshHomeData()
    }

    fun loadUserData() {
        val user = api.currentUser ?: return
        thread(name = "CloudMusic-UserData-Load", isDaemon = true) {
            try {
                userPlaylists = api.fetchUserPlaylists(user.id)
                // Load liked playlist tracks if available
                if (userPlaylists.isNotEmpty()) {
                    val first = userPlaylists[0] // Usually "Favorite Songs"
                    val (_, tracks) = api.fetchPlaylistDetail(first.id)
                    likedSongIds.clear()
                    likedSongIds.addAll(tracks.map { it.id })
                }
            } catch (e: Exception) {
                System.err.println("[CloudMusicManager] Error loading user playlists: ${e.message}")
            }
        }
    }

    fun refreshHomeData(force: Boolean = false) {
        if (isFetchingHome.getAndSet(true)) return
        thread(name = "CloudMusic-Home-Fetch", isDaemon = true) {
            try {
                if (force || dailySongs.isEmpty()) {
                    if (api.isLogged) {
                        try {
                            dailySongs = api.fetchDailySongs()
                        } catch (e: Exception) {
                            System.err.println("[CloudMusicManager] Failed to fetch daily songs: ${e.message}")
                        }
                    }
                }
                if (force || dailyPlaylists.isEmpty()) {
                    if (api.isLogged) {
                        try {
                            dailyPlaylists = api.fetchDailyPlaylists()
                        } catch (e: Exception) {
                            System.err.println("[CloudMusicManager] Failed to fetch daily playlists: ${e.message}")
                        }
                    }
                }
                if (force || topPlaylists.isEmpty()) {
                    try {
                        topPlaylists = api.fetchTopPlaylists(limit = 24)
                    } catch (e: Exception) {
                        System.err.println("[CloudMusicManager] Failed to fetch top playlists: ${e.message}")
                    }
                }
                try {
                    val fetched = api.fetchRankLists()
                    if (fetched.isNotEmpty()) {
                        topCharts = fetched
                    }
                } catch (e: Exception) {
                    System.err.println("[CloudMusicManager] Failed to fetch rank lists: ${e.message}")
                }
            } finally {
                isFetchingHome.set(false)
            }
        }
    }

    fun isSongLiked(songId: Long): Boolean = likedSongIds.contains(songId)

    fun toggleLikeSong(track: MusicTrack, onComplete: ((Boolean) -> Unit)? = null) {
        val currentlyLiked = likedSongIds.contains(track.id)
        val target = !currentlyLiked
        thread(name = "CloudMusic-Like-Toggle", isDaemon = true) {
            val success = api.likeSong(track.id, target)
            if (success) {
                if (target) {
                    likedSongIds.add(track.id)
                } else {
                    likedSongIds.remove(track.id)
                }
            }
            onComplete?.invoke(success)
        }
    }

    fun startQRLogin(
        onQRCodeReady: (String, ByteArray) -> Unit,
        onStatusUpdate: (QRStatus, String) -> Unit
    ) {
        stopQRLogin()
        qrPollingActive.set(true)

        qrPollingThread = thread(name = "CloudMusic-QR-Polling", isDaemon = true) {
            try {
                val unikey = api.requestQRKey()
                val pngBytes = api.generateQRCodePng(unikey, 220)
                onQRCodeReady(unikey, pngBytes)
                onStatusUpdate(QRStatus.WAITING, "Scan QR code with NetEase Music App")

                var attempts = 45 // 45 * 2s = 90s timeout
                while (qrPollingActive.get() && attempts > 0) {
                    Thread.sleep(2000)
                    if (!qrPollingActive.get()) break

                    val (status, cookie) = api.checkQRStatus(unikey)
                    when (status) {
                        QRStatus.WAITING -> {
                            onStatusUpdate(QRStatus.WAITING, "Waiting for QR scan...")
                        }
                        QRStatus.CONFIRMING -> {
                            onStatusUpdate(QRStatus.CONFIRMING, "Scanned! Please confirm on your mobile device")
                        }
                        QRStatus.SUCCESS -> {
                            onStatusUpdate(QRStatus.SUCCESS, "Login successful, loading data...")
                            if (!cookie.isNullOrBlank()) {
                                api.setCookiesAndSave(cookie)
                                loadUserData()
                                refreshHomeData(force = true)
                            }
                            qrPollingActive.set(false)
                            return@thread
                        }
                        QRStatus.EXPIRED -> {
                            onStatusUpdate(QRStatus.EXPIRED, "QR code expired, click to refresh")
                            qrPollingActive.set(false)
                            return@thread
                        }
                        QRStatus.ERROR -> {
                            onStatusUpdate(QRStatus.ERROR, "Login error, please retry")
                        }
                    }
                    attempts--
                }

                if (qrPollingActive.get()) {
                    onStatusUpdate(QRStatus.EXPIRED, "QR code timed out, click to refresh")
                }
            } catch (e: Exception) {
                onStatusUpdate(QRStatus.ERROR, "Connection failed: ${e.message}")
            } finally {
                qrPollingActive.set(false)
            }
        }
    }

    fun stopQRLogin() {
        qrPollingActive.set(false)
        qrPollingThread?.interrupt()
        qrPollingThread = null
    }
}
