package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.render.hud.HUDModule
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.client.multiplayer.ClientSuggestionProvider

class HudCommand : Command(
    "hud",
    "Open the HUD visual layout editor",
    ".hud",
    "hudedit"
) {
    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name).executes {
            HUDModule.openEditor()
            1
        }
        dispatcher.register(root)
        aliases.forEach { alias ->
            dispatcher.register(literal(alias).executes {
                HUDModule.openEditor()
                1
            })
        }
    }
}
