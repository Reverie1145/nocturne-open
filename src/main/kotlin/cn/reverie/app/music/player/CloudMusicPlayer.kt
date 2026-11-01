package cn.reverie.app.music.player

import cn.reverie.app.music.api.*
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.*
import kotlin.concurrent.thread

/**
 * Audio engine for CloudMusic in Nocturne.
 * Uses Java Sound API + MP3SPI / JLayer with PCM decoding.
 */
class CloudMusicPlayer(private val api: CloudMusicApi) {

    enum class PlaybackState {
        IDLE, BUFFERING, PLAYING, PAUSED, ERROR
    }

    val playlist = mutableListOf<MusicTrack>()
    var currentIndex: Int = -1
        private set

    var currentTrack: MusicTrack? = null
        private set

    var playbackState = PlaybackState.IDLE
        private set

    var playMode = PlayMode.LOOP_ALL

    var isFmMode = false
        private set

    var volume: Float = 0.80f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyVolume()
        }

    var currentPositionMs: Long = 0L
        private set

    var durationMs: Long = 0L
        private set

    var currentLyric: LyricData = LyricData()
        private set

    private var activeLine: SourceDataLine? = null
    private val isStopping = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var playerThread: Thread? = null

    private var playbackStartMs = 0L
    private var pausedAccumulatedMs = 0L
    private var pauseTimestamp = 0L

    // Seek support: byte offset or target ms
    @Volatile
    private var pendingSeekMs: Long? = null

    // Listeners for UI notification
    var onTrackChanged: ((MusicTrack?) -> Unit)? = null
    var onStateChanged: ((PlaybackState) -> Unit)? = null

    fun playTrack(track: MusicTrack) {
        isFmMode = false
        val idx = playlist.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            currentIndex = idx
        } else {
            playlist.add(0, track)
            currentIndex = 0
        }
        startPlayInternal(track)
    }

    fun playPlaylist(tracks: List<MusicTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        isFmMode = false
        playlist.clear()
        playlist.addAll(tracks)
        val validIndex = startIndex.coerceIn(0, playlist.size - 1)
        currentIndex = validIndex
        startPlayInternal(playlist[currentIndex])
    }

    fun playFm(initialTracks: List<MusicTrack>) {
        isFmMode = true
        playlist.clear()
        playlist.addAll(initialTracks)
        currentIndex = 0
        if (playlist.isNotEmpty()) {
            startPlayInternal(playlist[0])
        }
    }

    fun togglePlay() {
        when (playbackState) {
            PlaybackState.PLAYING -> pause()
            PlaybackState.PAUSED -> resume()
            PlaybackState.IDLE, PlaybackState.ERROR -> {
                if (currentTrack != null) {
                    startPlayInternal(currentTrack!!)
                } else if (playlist.isNotEmpty()) {
                    currentIndex = currentIndex.coerceIn(0, playlist.size - 1)
                    startPlayInternal(playlist[currentIndex])
                }
            }
            PlaybackState.BUFFERING -> {}
        }
    }

    fun pause() {
        if (playbackState == PlaybackState.PLAYING) {
            isPaused.set(true)
            pauseTimestamp = System.currentTimeMillis()
            playbackState = PlaybackState.PAUSED
            onStateChanged?.invoke(playbackState)
        }
    }

    fun resume() {
        if (playbackState == PlaybackState.PAUSED) {
            if (pauseTimestamp > 0) {
                pausedAccumulatedMs += (System.currentTimeMillis() - pauseTimestamp)
                pauseTimestamp = 0
            }
            isPaused.set(false)
            playbackState = PlaybackState.PLAYING
            onStateChanged?.invoke(playbackState)
        }
    }

    fun stop() {
        isStopping.set(true)
        activeLine?.stop()
        activeLine?.close()
        activeLine = null
        playbackState = PlaybackState.IDLE
        currentPositionMs = 0
        onStateChanged?.invoke(playbackState)
    }

    fun next() {
        if (playlist.isEmpty()) return

        if (isFmMode) {
            currentIndex++
            if (currentIndex >= playlist.size) {
                // Fetch next batch of FM tracks
                thread(name = "CloudMusic-FM-Next", isDaemon = true) {
                    val more = api.fetchPersonalFm()
                    if (more.isNotEmpty()) {
                        playlist.addAll(more)
                        startPlayInternal(playlist[currentIndex])
                    } else {
                        stop()
                    }
                }
                return
            } else {
                startPlayInternal(playlist[currentIndex])
                return
            }
        }

        when (playMode) {
            PlayMode.LOOP_ALL -> {
                currentIndex = (currentIndex + 1) % playlist.size
            }
            PlayMode.SINGLE_LOOP -> {
                // Keep same index
            }
            PlayMode.SHUFFLE -> {
                currentIndex = (0 until playlist.size).random()
            }
        }
        startPlayInternal(playlist[currentIndex])
    }

    fun previous() {
        if (playlist.isEmpty()) return
        if (isFmMode) return // FM has no prev

        currentIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        startPlayInternal(playlist[currentIndex])
    }

    fun cyclePlayMode() {
        playMode = when (playMode) {
            PlayMode.LOOP_ALL -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LOOP_ALL
        }
    }

    fun seekTo(timeMs: Long) {
        if (durationMs <= 0) return
        val targetMs = timeMs.coerceIn(0L, durationMs)
        pendingSeekMs = targetMs
    }

    fun getActiveLyricState(): LyricState {
        return currentLyric.getActiveState(currentPositionMs)
    }

    private fun startPlayInternal(track: MusicTrack) {
        stop()
        currentTrack = track
        durationMs = track.durationMs
        currentPositionMs = 0L
        playbackState = PlaybackState.BUFFERING
        onTrackChanged?.invoke(track)
        onStateChanged?.invoke(playbackState)

        // Load lyrics in background
        thread(name = "CloudMusic-Lyric-Fetch", isDaemon = true) {
            currentLyric = api.fetchLyric(track.id)
        }

        isStopping.set(false)
        isPaused.set(false)
        pauseTimestamp = 0
        pausedAccumulatedMs = 0

        playerThread = thread(name = "CloudMusic-Audio-Thread", isDaemon = true) {
            runAudioLoop(track)
        }
    }

    private fun runAudioLoop(track: MusicTrack) {
        try {
            // 1. Get audio file or stream URL
            var audioFile: File? = CloudMusicCache.getCachedFile(track.id)
            if (audioFile == null) {
                val url = api.fetchSongAudioUrl(track.id)
                if (url.isNullOrBlank()) {
                    System.err.println("[CloudMusicPlayer] Failed to get audio stream URL for ${track.name}")
                    playbackState = PlaybackState.ERROR
                    onStateChanged?.invoke(playbackState)
                    return
                }
                audioFile = CloudMusicCache.downloadToFile(track.id, url)
                if (audioFile == null) {
                    playbackState = PlaybackState.ERROR
                    onStateChanged?.invoke(playbackState)
                    return
                }
            }

            if (isStopping.get()) return

            // 2. Open AudioInputStream and decode to PCM
            var rawStream = AudioSystem.getAudioInputStream(audioFile)
            val baseFormat = rawStream.format

            var decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.sampleRate,
                16,
                baseFormat.channels,
                baseFormat.channels * 2,
                baseFormat.sampleRate,
                false
            )
            var pcmStream = AudioSystem.getAudioInputStream(decodedFormat, rawStream)

            val lineInfo = DataLine.Info(SourceDataLine::class.java, decodedFormat)
            val line = AudioSystem.getLine(lineInfo) as SourceDataLine
            line.open(decodedFormat)
            activeLine = line
            applyVolume()
            line.start()

            playbackState = PlaybackState.PLAYING
            onStateChanged?.invoke(playbackState)

            playbackStartMs = System.currentTimeMillis()
            val buffer = ByteArray(4096)
            var totalBytesRead = 0L
            val frameSize = decodedFormat.frameSize.coerceAtLeast(1)
            val frameRate = decodedFormat.frameRate.coerceAtLeast(1f)

            while (!isStopping.get()) {
                // Check if user requested a seek
                val targetSeek = pendingSeekMs
                if (targetSeek != null) {
                    pendingSeekMs = null
                    try {
                        line.stop()
                        line.flush()
                        pcmStream.close()
                        rawStream.close()

                        rawStream = AudioSystem.getAudioInputStream(audioFile)
                        pcmStream = AudioSystem.getAudioInputStream(decodedFormat, rawStream)

                        val bytesPerSecond = (frameSize * frameRate).toLong()
                        val targetBytes = (targetSeek * bytesPerSecond) / 1000L
                        pcmStream.skip(targetBytes)
                        totalBytesRead = targetBytes

                        playbackStartMs = System.currentTimeMillis() - targetSeek
                        pausedAccumulatedMs = 0
                        line.start()
                    } catch (e: Exception) {
                        System.err.println("[CloudMusicPlayer] Seek failed: ${e.message}")
                    }
                }

                // Handle pause
                if (isPaused.get()) {
                    line.stop()
                    while (isPaused.get() && !isStopping.get()) {
                        Thread.sleep(20)
                    }
                    if (!isStopping.get()) {
                        line.start()
                    }
                }

                val read = pcmStream.read(buffer, 0, buffer.size)
                if (read == -1) {
                    // Song completed normally
                    break
                }

                if (read > 0) {
                    AudioVisualizer.processPcm(buffer, read)
                }

                line.write(buffer, 0, read)
                totalBytesRead += read

                // Calculate current position
                val bytesPerSecond = (frameSize * frameRate).toDouble()
                if (bytesPerSecond > 0) {
                    currentPositionMs = ((totalBytesRead / bytesPerSecond) * 1000.0).toLong()
                } else {
                    currentPositionMs = System.currentTimeMillis() - playbackStartMs - pausedAccumulatedMs
                }
            }

            line.drain()
            line.stop()
            line.close()
            pcmStream.close()
            rawStream.close()

            if (!isStopping.get()) {
                // Auto play next track
                next()
            }
        } catch (e: Exception) {
            if (!isStopping.get()) {
                System.err.println("[CloudMusicPlayer] Error during audio playback: ${e.message}")
                e.printStackTrace()
                playbackState = PlaybackState.ERROR
                onStateChanged?.invoke(playbackState)
            }
        } finally {
            activeLine = null
        }
    }

    private fun applyVolume() {
        val line = activeLine ?: return
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val min = gain.minimum
                val max = gain.maximum
                val db = if (volume <= 0.001f) min else (20f * Math.log10(volume.toDouble())).toFloat().coerceIn(min, max)
                gain.value = db
            }
        } catch (ignored: Exception) {}
    }
}
