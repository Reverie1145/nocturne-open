package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider

class VClipCommand : Command(
    "vclip",
    "Teleport vertically by a specified number of blocks",
    ".vclip <distance>",
    "vc"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .then(
                argument("distance", DoubleArgumentType.doubleArg(-256.0, 256.0))
                    .executes { context ->
                        val distance = DoubleArgumentType.getDouble(context, "distance")
                        val player = Minecraft.getInstance().player
                        if (player == null) {
                            ChatUtil.sendError("You must be in game to use vclip!")
                            return@executes 0
                        }

                        player.setPos(player.x, player.y + distance, player.z)
                        ChatUtil.sendSuccess("Teleported ${if (distance >= 0) "+$distance" else "$distance"} blocks vertically.")
                        1
                    }
            )

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }
}
