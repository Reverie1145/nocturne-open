package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.app.config.ConfigManager
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

class ConfigCommand : Command(
    "config",
    "Manage client profiles and configurations",
    ".config <save|load|list|delete|reload> [name]",
    "cfg"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)

            .then(
                literal("list")
                    .executes {
                        listConfigs()
                        1
                    }
            )

            .then(
                literal("reload")
                    .executes {
                        val active = ConfigManager.activeConfig
                        if (ConfigManager.loadConfig(active)) {
                            ChatUtil.sendSuccess("Reloaded active configuration '$active'.")
                        } else {
                            ChatUtil.sendError("Failed to reload configuration '$active'.")
                        }
                        1
                    }
            )

            .then(
                literal("save")
                    .then(
                        argument("name", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(ConfigManager.listConfigs(), builder)
                            }
                            .executes { context ->
                                val name = StringArgumentType.getString(context, "name")
                                if (ConfigManager.saveConfig(name)) {
                                    ChatUtil.sendSuccess("Saved configuration '$name'.")
                                } else {
                                    ChatUtil.sendError("Failed to save configuration '$name'.")
                                }
                                1
                            }
                    )
            )

            .then(
                literal("load")
                    .then(
                        argument("name", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(ConfigManager.listConfigs(), builder)
                            }
                            .executes { context ->
                                val name = StringArgumentType.getString(context, "name")
                                if (ConfigManager.loadConfig(name)) {
                                    ChatUtil.sendSuccess("Loaded configuration '$name'.")
                                } else {
                                    ChatUtil.sendError("Configuration '$name' does not exist or failed to load.")
                                }
                                1
                            }
                    )
            )

            .then(
                literal("delete")
                    .then(
                        argument("name", StringArgumentType.word())
                            .suggests { _, builder ->
                                SharedSuggestionProvider.suggest(ConfigManager.listConfigs(), builder)
                            }
                            .executes { context ->
                                val name = StringArgumentType.getString(context, "name")
                                if (ConfigManager.deleteConfig(name)) {
                                    ChatUtil.sendSuccess("Deleted configuration '$name'.")
                                } else {
                                    ChatUtil.sendError("Configuration '$name' not found or failed to delete.")
                                }
                                1
                            }
                    )
            )
            .executes {
                ChatUtil.sendWarning("Usage: $syntax")
                1
            }

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun listConfigs() {
        val configs = ConfigManager.listConfigs()
        if (configs.isEmpty()) {
            ChatUtil.sendInfo("No saved configurations found in nocturne/configs/.")
            return
        }

        ChatUtil.send(Component.literal("=== Saved Configurations ===").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        for (cfg in configs) {
            val isActive = cfg == ConfigManager.activeConfig
            val item = Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(cfg).withStyle(if (isActive) ChatFormatting.GREEN else ChatFormatting.WHITE))
            if (isActive) {
                item.append(Component.literal(" [ACTIVE]").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
            }
            ChatUtil.send(item)
        }
    }
}
