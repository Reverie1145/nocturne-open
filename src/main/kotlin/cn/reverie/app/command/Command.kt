package cn.reverie.app.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import net.minecraft.client.multiplayer.ClientSuggestionProvider

abstract class Command(
    val name: String,
    val description: String,
    val syntax: String,
    vararg val aliases: String
) {

    abstract fun register(dispatcher: CommandDispatcher<ClientSuggestionProvider>)

    protected fun literal(name: String): LiteralArgumentBuilder<ClientSuggestionProvider> {
        return LiteralArgumentBuilder.literal(name)
    }

    protected fun <T> argument(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<ClientSuggestionProvider, T> {
        return RequiredArgumentBuilder.argument(name, type)
    }
}
