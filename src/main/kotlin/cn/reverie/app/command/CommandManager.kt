package cn.reverie.app.command

import cn.reverie.app.command.impl.*
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.Commands

object CommandManager {

    val dispatcher = CommandDispatcher<ClientSuggestionProvider>()
    private val commandList = mutableListOf<Command>()
    val commands: List<Command> get() = commandList

    init {
        registerBuiltinCommands()
    }

    private fun registerBuiltinCommands() {
        register(HelpCommand())
        register(ToggleCommand())
        register(BindCommand())
        register(ConfigCommand())
        register(SettingCommand())
        register(ModulesCommand())
        register(VClipCommand())
        register(HudCommand())
        register(AccountCommand())
        register(MusicCommand())
        register(AgentCommand())
        register(ScriptCommand())
    }

    fun register(command: Command) {
        if (!commandList.contains(command)) {
            commandList.add(command)
            command.register(dispatcher)
        }
    }

    fun getCommand(name: String): Command? {
        return commandList.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.aliases.any { a -> a.equals(name, ignoreCase = true) }
        }
    }

    fun execute(commandLine: String) {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return

        val mc = Minecraft.getInstance()
        val player = mc.player
        val connection = player?.connection
        val source = connection?.suggestionsProvider

        if (source == null) {
            ChatUtil.sendError("Cannot execute command outside of a game session!")
            return
        }

        try {
            val parseResults = dispatcher.parse(trimmed, source)
            if (parseResults.reader.canRead()) {
                val ex = Commands.getParseException(parseResults)
                if (ex != null) {
                    ChatUtil.sendError(ex.rawMessage.string)
                } else {
                    ChatUtil.sendError("Unknown or incomplete command: .$trimmed")
                }
                return
            }

            dispatcher.execute(parseResults)
        } catch (e: CommandSyntaxException) {
            ChatUtil.sendError(e.rawMessage.string)
        } catch (e: Exception) {
            ChatUtil.sendError("Error executing command: ${e.message ?: e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }
}
