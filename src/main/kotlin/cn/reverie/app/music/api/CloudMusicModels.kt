package cn.reverie.app.music.api

/**
 * Data structures for NetEase Cloud Music in Nocturne.
 */

data class MusicTrack(
    val id: Long,
    val name: String,
    val artists: String,
    val albumName: String,
    val durationMs: Long,
    val picUrl: String,
    val alias: String = "",
    val fee: Int = 0 // 0: free, 1: VIP, 4: paid album, 8: low quality free
) {
    val durationFormatted: String
        get() {
            val totalSec = (durationMs / 1000).toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%02d:%02d".format(min, sec)
        }
}

data class MusicPlaylist(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val trackCount: Int,
    val playCount: Long = 0,
    val creatorName: String = "",
    val creatorId: Long = 0,
    val description: String = "",
    val tags: List<String> = emptyList()
) {
    val playCountFormatted: String
        get() {
            return when {
                playCount >= 1_000_000_000 -> "%.1fB".format(playCount / 1_000_000_000.0)
                playCount >= 1_000_000 -> "%.1fM".format(playCount / 1_000_000.0)
                playCount >= 1_000 -> "%.1fK".format(playCount / 1_000.0)
                else -> playCount.toString()
            }
        }
}

data class MusicUser(
    val id: Long,
    val name: String,
    val avatarUrl: String,
    val signature: String = "",
    val vipType: Int = 0,
    val level: Int = 0,
    val listenSongs: Long = 0,
    val playlistCount: Int = 0
) {
    val isVip: Boolean get() = vipType > 0
}

data class MusicArtist(
    val id: Long,
    val name: String,
    val avatarUrl: String = "",
    val musicSize: Int = 0,
    val albumSize: Int = 0
)

data class MusicAlbum(
    val id: Long,
    val name: String,
    val coverUrl: String,
    val artistName: String,
    val size: Int = 0,
    val publishTime: Long = 0
)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)

data class LyricData(
    val lines: List<LyricLine> = emptyList()
) {
    fun findLineIndex(timeMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= timeMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun getActiveState(timeMs: Long): LyricState {
        if (lines.isEmpty()) {
            return LyricState("", "", "", "", 0f, -1)
        }
        val idx = findLineIndex(timeMs)
        val current = lines[idx]
        var nextText = ""
        var nextTrans = ""
        var progress = 0f

        if (idx + 1 < lines.size) {
            val next = lines[idx + 1]
            nextText = next.text
            nextTrans = next.translation
            val span = (next.timeMs - current.timeMs).coerceAtLeast(1L)
            val raw = ((timeMs - current.timeMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            progress = raw * raw * (3f - 2f * raw) // Smooth bezier ease
        }

        return LyricState(
            current = current.text,
            translation = current.translation,
            next = nextText,
            nextTranslation = nextTrans,
            progress = progress,
            lineIndex = idx
        )
    }
}

data class LyricState(
    val current: String,
    val translation: String,
    val next: String,
    val nextTranslation: String,
    val progress: Float,
    val lineIndex: Int
)

enum class QRStatus(val code: Int) {
    WAITING(801),
    CONFIRMING(802),
    SUCCESS(803),
    EXPIRED(800),
    ERROR(-1)
}

enum class PlayMode(val title: String) {
    LOOP_ALL("Loop All"),
    SINGLE_LOOP("Single Loop"),
    SHUFFLE("Shuffle")
}
