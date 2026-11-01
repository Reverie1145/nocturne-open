package cn.reverie.app.command.impl

import cn.reverie.app.command.Command
import cn.reverie.module.*
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.misc.ChatUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.ChatFormatting
import net.minecraft.client.multiplayer.ClientSuggestionProvider
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.Component

class SettingCommand : Command(
    "setting",
    "View or change module settings",
    ".setting <module> <setting> [value]",
    "set"
) {

    override fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>) {
        val root = literal(name)
            .then(
                argument("module", StringArgumentType.word())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(ModuleManager.modules.map { it.name }, builder)
                    }
                    .then(
                        argument("setting", StringArgumentType.word())
                            .suggests { context, builder ->
                                val modName = try {
                                    StringArgumentType.getString(context, "module")
                                } catch (e: Exception) {
                                    null
                                }
                                val mod = modName?.let { ModuleManager.getModuleByName(it) }
                                val settingNames = mod?.settings?.map { it.name } ?: emptyList()
                                SharedSuggestionProvider.suggest(settingNames, builder)
                            }
                            .then(
                                argument("value", StringArgumentType.greedyString())
                                    .suggests { context, builder ->
                                        val modName = try { StringArgumentType.getString(context, "module") } catch (e: Exception) { null }
                                        val setStr = try { StringArgumentType.getString(context, "setting") } catch (e: Exception) { null }
                                        val mod = modName?.let { ModuleManager.getModuleByName(it) }
                                        val setting = mod?.settings?.firstOrNull { it.name.equals(setStr, ignoreCase = true) }

                                        when (setting) {
                                            is BooleanSetting -> SharedSuggestionProvider.suggest(listOf("true", "false"), builder)
                                            is ModeSetting -> SharedSuggestionProvider.suggest(setting.modes, builder)
                                            is ColorSetting -> SharedSuggestionProvider.suggest(ColorMode.entries.map { it.name }, builder)
                                            else -> builder.buildFuture()
                                        }
                                    }
                                    .executes { context ->
                                        val modName = StringArgumentType.getString(context, "module")
                                        val setStr = StringArgumentType.getString(context, "setting")
                                        val valueStr = StringArgumentType.getString(context, "value")

                                        val mod = ModuleManager.getModuleByName(modName)
                                        if (mod == null) {
                                            ChatUtil.sendError("Module '$modName' not found!")
                                            return@executes 0
                                        }

                                        val setting = mod.settings.firstOrNull { it.name.equals(setStr, ignoreCase = true) }
                                        if (setting == null) {
                                            ChatUtil.sendError("Setting '$setStr' not found in module ${mod.name}!")
                                            return@executes 0
                                        }

                                        applySettingValue(mod, setting, valueStr)
                                        1
                                    }
                            )
                            .executes { context ->
                                val modName = StringArgumentType.getString(context, "module")
                                val setStr = StringArgumentType.getString(context, "setting")

                                val mod = ModuleManager.getModuleByName(modName)
                                if (mod == null) {
                                    ChatUtil.sendError("Module '$modName' not found!")
                                    return@executes 0
                                }

                                val setting = mod.settings.firstOrNull { it.name.equals(setStr, ignoreCase = true) }
                                if (setting == null) {
                                    ChatUtil.sendError("Setting '$setStr' not found in module ${mod.name}!")
                                    return@executes 0
                                }

                                displaySettingInfo(mod, setting)
                                1
                            }
                    )
            )

        dispatcher.register(root)
        for (alias in aliases) {
            dispatcher.register(literal(alias).redirect(dispatcher.root.getChild(name)))
        }
    }

    private fun displaySettingInfo(mod: Module, setting: Setting<*>) {
        val valStr = when (setting) {
            is BooleanSetting -> setting.value.toString()
            is NumberSetting -> "${setting.value} (Min: ${setting.min}, Max: ${setting.max}, Step: ${setting.step})"
            is ModeSetting -> "${setting.value} [Available: ${setting.modes.joinToString(", ")}]"
            is ColorSetting -> "${setting.primaryColor.toHex(true)} (Mode: ${setting.colorMode.displayName})"
            else -> setting.value.toString()
        }

        val msg = Component.literal("${mod.name}").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(" -> ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(setting.name).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(valStr).withStyle(ChatFormatting.WHITE))

        ChatUtil.send(msg)
    }

    private fun applySettingValue(mod: Module, setting: Setting<*>, valueStr: String) {
        when (setting) {
            is BooleanSetting -> {
                val b = valueStr.toBooleanStrictOrNull()
                if (b != null) {
                    setting.value = b
                    ChatUtil.sendSuccess("Set ${mod.name} ${setting.name} to $b.")
                } else {
                    ChatUtil.sendError("Invalid boolean value '$valueStr'. Use true or false.")
                }
            }
            is NumberSetting -> {
                val d = valueStr.toDoubleOrNull()
                if (d != null) {
                    setting.setClamped(d)
                    ChatUtil.sendSuccess("Set ${mod.name} ${setting.name} to ${setting.value}.")
                } else {
                    ChatUtil.sendError("Invalid number '$valueStr'. Min: ${setting.min}, Max: ${setting.max}")
                }
            }
            is ModeSetting -> {
                val matched = setting.modes.firstOrNull { it.equals(valueStr, ignoreCase = true) }
                if (matched != null) {
                    setting.value = matched
                    ChatUtil.sendSuccess("Set ${mod.name} ${setting.name} to $matched.")
                } else {
                    ChatUtil.sendError("Invalid mode '$valueStr'. Available: ${setting.modes.joinToString(", ")}")
                }
            }
            is ColorSetting -> {
                try {
                    val color = NanoVGColor.fromHex(valueStr)
                    setting.setPrimaryColor(color)
                    ChatUtil.sendSuccess("Set ${mod.name} ${setting.name} color to ${color.toHex(true)}.")
                } catch (e: Exception) {
                    ChatUtil.sendError("Invalid hex color '$valueStr'. Example: #FF5555 or #FFFF5555")
                }
            }
            else -> {
                ChatUtil.sendError("Setting type not directly modifiable via chat.")
            }
        }
    }
}
