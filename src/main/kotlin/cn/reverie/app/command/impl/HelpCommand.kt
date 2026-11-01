package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.app.command.CommandManager
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

class HelpCommand : Command(
    "help",
    "Display help information and command list",
    ".help [command]",
    "h", "?"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .executes {
                displayAllCommands()
                1
            }
            .then(
                argument("command", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(CommandManager.commands.map { it.name }, builder)
                    }
                    .executes { context ->
                        val cmdName = StringArgumentType.getString(context, "command")
                        displayCommandHelp(cmdName)
                        1
                    }
            )

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun displayAllCommands() {
        ChatUtil.send(Component.literal("=== Available Nocturne Commands ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        for (cmd in CommandManager.commands.sortedBy { it.name }) {
            val msg = Component.literal(".${cmd.name}").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(cmd.description).withStyle(ChatFormatting.GRAY))
            ChatUtil.send(msg)
        }
        ChatUtil.send(Component.literal("Type .help <command> for detailed usage.").withStyle(ChatFormatting.DARK_AQUA))
    }

    private fun displayCommandHelp(cmdName: String) {
        val cmd = CommandManager.getCommand(cmdName)
        if (cmd == null) {
            ChatUtil.sendError("Command '.$cmdName' not found! Type .help to view all commands.")
            return
        }

        ChatUtil.send(Component.literal("=== Command: .${cmd.name} ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        ChatUtil.send(Component.literal("Description: ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.description).withStyle(ChatFormatting.WHITE)))
        ChatUtil.send(Component.literal("Syntax: ").withStyle(ChatFormatting.GRAY).append(Component.literal(cmd.syntax).withStyle(ChatFormatting.YELLOW)))
        if (cmd.aliases.isNotEmpty()) {
            val aliasStr = cmd.aliases.joinToString(", ") { ".$it" }
            ChatUtil.send(Component.literal("Aliases: ").withStyle(ChatFormatting.GRAY).append(Component.literal(aliasStr).withStyle(ChatFormatting.GOLD)))
        }
    }
}
