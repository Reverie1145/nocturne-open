package cn.reverie.util.misc

import cn.reverie.render.hud.HUDModule
import cn.reverie.module.render.NameProtect
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence

object ChatUtil {

    private const val CLIENT_PREFIX = "[Nocturne] "

    private fun getPrefix(): MutableComponent {
        return Component.literal("[")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Nocturne").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
    }

    @JvmStatic
    fun animateNocturnePrefix(message: FormattedCharSequence): FormattedCharSequence {
        val protectedMessage = NameProtect.protectChatSequence(message)
        val prefix = StringBuilder(CLIENT_PREFIX.length)
        protectedMessage.accept { _, _, codepoint ->
            if (prefix.length < CLIENT_PREFIX.length) {
                prefix.appendCodePoint(codepoint)
            }
            prefix.length < CLIENT_PREFIX.length
        }
        if (!prefix.toString().startsWith(CLIENT_PREFIX)) return protectedMessage

        return FormattedCharSequence { sink ->
            var characterIndex = 0
            protectedMessage.accept { index, style, codepoint ->
                val renderedStyle: Style = if (characterIndex in 1..8) {
                    style.withColor(HUDModule.sampleColor((characterIndex - 1) * 0.12f).toArgb() and 0xFFFFFF)
                } else {
                    style
                }
                characterIndex++
                sink.accept(index, renderedStyle, codepoint)
            }
        }
    }

    fun send(component: Component) {
        val mc = Minecraft.getInstance()
        val chat = mc.gui.hud.getChat()
        chat.addClientSystemMessage(getPrefix().append(component))
    }

    fun send(message: String) {
        send(Component.literal(message).withStyle(ChatFormatting.GRAY))
    }

    fun sendSuccess(message: String) {
        send(Component.literal(message).withStyle(ChatFormatting.GREEN))
    }

    fun sendError(message: String) {
        send(Component.literal(message).withStyle(ChatFormatting.RED))
    }

    fun sendWarning(message: String) {
        send(Component.literal(message).withStyle(ChatFormatting.YELLOW))
    }

    fun sendInfo(message: String) {
        send(Component.literal(message).withStyle(ChatFormatting.WHITE))
    }
}
