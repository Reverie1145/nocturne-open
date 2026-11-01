package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.module.ModuleManager
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

class ToggleCommand : Command(
    "toggle",
    "Toggle a client module on or off",
    ".toggle <module>",
    "t"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .then(
                argument("module", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ModuleManager.modules.map { it.name }, builder)
                    }
                    .executes { context ->
                        val moduleName = StringArgumentType.getString(context, "module")
                        val module = ModuleManager.getModuleByName(moduleName)
                        if (module == null) {
                            ChatUtil.sendError("Module '$moduleName' not found!")
                            return@executes 0
                        }

                        module.toggle()
                        val stateText = if (module.enabled) {
                            Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        } else {
                            Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        }

                        val msg = Component.literal("Module ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(module.name).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" is now ").withStyle(ChatFormatting.GRAY))
                            .append(stateText)

                        ChatUtil.send(msg)
                        1
                    }
            )

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }
}
