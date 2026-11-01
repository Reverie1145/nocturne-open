package cn.reverie.nocturne

import cn.reverie.app.music.api.MusicTrack
import cn.reverie.app.music.player.CloudMusicPlayer

/**
 * IRC config/music sharing — UI shell only.
 *
 * The chunked, consent-based share protocol was carried over IRC PRIVMSGs, so
 * it went away with the transport. The data classes are kept because the click
 * GUI renders them; the lists are simply always empty.
 */
object IrcShareManager {

    data class NowPlayingEntry(
        val nick: String,
        val state: String,
        val trackId: Long,
        val title: String,
        val artists: String,
        val updatedAt: Long
    )

    data class PendingOfferEntry(val id: String, val sender: String, val kind: String)

    fun onIncoming(sender: String, text: String, direct: Boolean): Boolean = false

    fun handleCommand(input: String): Boolean = false

    fun onLocalMusicChanged(track: MusicTrack?, state: CloudMusicPlayer.PlaybackState) {}

    fun publishNowPlaying() {}

    fun requestNowPlaying() {}

    fun nowPlayingEntries(): List<NowPlayingEntry> = emptyList()

    fun pendingOffers(): List<PendingOfferEntry> = emptyList()

    fun acceptOffer(id: String): Boolean? = null

    fun rejectOffer(id: String): Boolean = false

    fun sendCurrentConfig(target: String) {}

    fun sendModuleConfig(target: String, moduleName: String) {}

    fun shareCurrentMusic(target: String) {}

    fun shareMusic(target: String, track: MusicTrack?) {}

    fun onMusicPresence(wireValue: String) {}
}
