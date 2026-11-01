package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.module.Category
import cn.reverie.module.ModuleManager
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.network.chat.Component

class ModulesCommand : Command(
    "modules",
    "List all client modules by category",
    ".modules",
    "list", "mods"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .executes {
                displayModules()
                1
            }

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun displayModules() {
        val total = ModuleManager.modules.size
        val enabledCount = ModuleManager.modules.count { it.enabled }

        ChatUtil.send(
            Component.literal("=== Modules ($enabledCount/$total Enabled) ===")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
        )

        for (category in Category.entries) {
            val mods = ModuleManager.getModulesByCategory(category)
            if (mods.isEmpty()) continue

            val line = Component.literal("${category.displayName}: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            for ((index, mod) in mods.withIndex()) {
                val modColor = if (mod.enabled) ChatFormatting.GREEN else ChatFormatting.RED
                line.append(Component.literal(mod.name).withStyle(modColor))
                if (index < mods.size - 1) {
                    line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY))
                }
            }
            ChatUtil.send(line)
        }
    }
}
