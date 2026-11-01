package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.module.ModuleManager
import cn.reverie.util.misc.ChatUtil
import cn.reverie.util.misc.KeyUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

class BindCommand : Command(
    "bind",
    "Bind or unbind a module to a key",
    ".bind <module> <key|clear|none>",
    "b"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .then(
                argument("module", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ModuleManager.modules.map { it.name }, builder)
                    }
                    .then(
                        argument("key", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(KeyUtil.getAllKeyNames(), builder)
                            }
                            .executes { context ->
                                val moduleName = StringArgumentType.getString(context, "module")
                                val keyStr = StringArgumentType.getString(context, "key")

                                val module = ModuleManager.getModuleByName(moduleName)
                                if (module == null) {
                                    ChatUtil.sendError("Module '$moduleName' not found!")
                                    return@executes 0
                                }

                                val keyCode = KeyUtil.getKeyCode(keyStr)
                                module.keyCode = keyCode

                                if (keyCode == GLFW.GLFW_KEY_UNKNOWN) {
                                    val msg = Component.literal("Unbound ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(module.name).withStyle(ChatFormatting.AQUA))
                                        .append(Component.literal(" from any key.").withStyle(ChatFormatting.GRAY))
                                    ChatUtil.send(msg)
                                } else {
                                    val keyName = KeyUtil.getKeyName(keyCode)
                                    val msg = Component.literal("Bound ").withStyle(ChatFormatting.GRAY)
                                        .append(Component.literal(module.name).withStyle(ChatFormatting.AQUA))
                                        .append(Component.literal(" to key ").withStyle(ChatFormatting.GRAY))
                                        .append(Component.literal(keyName).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                    ChatUtil.send(msg)
                                }
                                1
                            }
                    )
                    .executes { context ->
                        val moduleName = StringArgumentType.getString(context, "module")
                        val module = ModuleManager.getModuleByName(moduleName)
                        if (module == null) {
                            ChatUtil.sendError("Module '$moduleName' not found!")
                            return@executes 0
                        }

                        val keyName = KeyUtil.getKeyName(module.keyCode)
                        val msg = Component.literal("Module ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(module.name).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" is currently bound to ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(keyName).withStyle(ChatFormatting.YELLOW))
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
