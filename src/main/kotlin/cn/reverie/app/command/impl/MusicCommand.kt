package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.app.music.CloudMusicManager
import cn.reverie.app.music.player.CloudMusicPlayer
import cn.reverie.app.music.ui.CloudMusicScreen
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import kotlin.concurrent.thread

/**
 * MusicCommand provides chat-based controls and opens the CloudMusic GUI.
 */
class MusicCommand : Command(
    "music",
    "Control CloudMusic playback or open the modern music player GUI",
    ".music [play|pause|next|prev|fm|like|vol <0-100>|search <keywords>]",
    "cloudmusic", "cm"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .executes {
                openScreen()
                1
            }
            .then(literal("play").executes {
                CloudMusicManager.player.resume()
                ChatUtil.sendSuccess("Resumed playback.")
                1
            })
            .then(literal("pause").executes {
                CloudMusicManager.player.pause()
                ChatUtil.sendInfo("Paused playback.")
                1
            })
            .then(literal("toggle").executes {
                CloudMusicManager.player.togglePlay()
                1
            })
            .then(literal("next").executes {
                CloudMusicManager.player.next()
                ChatUtil.sendInfo("Playing next song.")
                1
            })
            .then(literal("prev").executes {
                CloudMusicManager.player.previous()
                ChatUtil.sendInfo("Playing previous song.")
                1
            })
            .then(literal("like").executes {
                val track = CloudMusicManager.player.currentTrack
                if (track != null) {
                    CloudMusicManager.toggleLikeSong(track) { success ->
                        if (success) {
                            val liked = CloudMusicManager.isSongLiked(track.id)
                            ChatUtil.sendSuccess(if (liked) "Added ${track.name} to favorites ❤️" else "Removed ${track.name} from favorites.")
                        } else {
                            ChatUtil.sendError("Failed to update favorite status.")
                        }
                    }
                } else {
                    ChatUtil.sendError("No song currently playing.")
                }
                1
            })
            .then(literal("fm").executes {
                thread(name = "CloudMusic-Cmd-FM", isDaemon = true) {
                    val tracks = CloudMusicManager.api.fetchPersonalFm()
                    if (tracks.isNotEmpty()) {
                        CloudMusicManager.player.playFm(tracks)
                        ChatUtil.sendSuccess("Started Personal FM 📻")
                    } else {
                        ChatUtil.sendError("Could not fetch FM radio songs.")
                    }
                }
                1
            })
            .then(
                literal("vol").then(
                    argument("volume", IntegerArgumentType.integer(0, 100)).executes { ctx ->
                        val v = IntegerArgumentType.getInteger(ctx, "volume")
                        CloudMusicManager.player.volume = v / 100f
                        ChatUtil.sendInfo("Volume set to $v%")
                        1
                    }
                )
            )
            .then(
                literal("search").then(
                    argument("keywords", StringArgumentType.greedyString()).executes { ctx ->
                        val keywords = StringArgumentType.getString(ctx, "keywords")
                        thread(name = "CloudMusic-Cmd-Search", isDaemon = true) {
                            val results = CloudMusicManager.api.searchTracks(keywords, limit = 5)
                            if (results.isNotEmpty()) {
                                CloudMusicManager.player.playPlaylist(results, 0)
                                val first = results[0]
                                ChatUtil.sendSuccess("Playing: ${first.name} - ${first.artists}")
                            } else {
                                ChatUtil.sendError("No search results found for: $keywords")
                            }
                        }
                        1
                    }
                )
            )

        dispatcher.register(root)
        aliases.forEach { alias ->
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun openScreen() {
        val mc = Minecraft.getInstance()
        mc.execute {
            val parent = mc.gui.screen()
            mc.gui.setScreen(CloudMusicScreen(parent))
        }
    }
}
