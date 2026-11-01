package cn.reverie.module.render

import cn.reverie.module.Category
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Module
import cn.reverie.module.StringSetting
import cn.reverie.render.font.TextSpan
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.nanovg.NanoVGColor
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import net.minecraft.world.entity.Entity
import java.util.UUID

object NameProtect : Module(
    name = "NameProtect",
    description = "Replaces your name in client-side player name displays",
    category = Category.RENDER
) {

    val replacement = StringSetting(
        "Replacement Name",
        "Name shown in place of your account name",
        "Protected"
    )
    val colorfulName = BooleanSetting(
        "Colorful Name",
        "Animate the protected name with Nocturne's color gradient",
        false
    )

    @JvmStatic
    fun replacementFor(entity: Entity): String? {
        val localPlayer = Minecraft.getInstance().player ?: return null
        if (entity !== localPlayer) return null
        return replacementForPlayer(localPlayer.uuid)
    }

    @JvmStatic
    fun replacementForPlayer(playerId: UUID): String? {
        if (!enabled) return null
        val localPlayer = Minecraft.getInstance().player ?: return null
        if (playerId != localPlayer.uuid) return null
        return replacement.value.trim().takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun protectDisplayName(original: Component, accountName: String, alias: String): Component {
        val visibleName = original.string
        if (accountName.isBlank()) return protectedNameComponent(alias, original.style)

        val matches = Regex(Regex.escape(accountName)).findAll(visibleName).toList()
        if (matches.isEmpty()) return protectedNameComponent(alias, original.style)

        val result = Component.literal("").withStyle(original.style)
        var cursor = 0
        for (match in matches) {
            if (match.range.first > cursor) {
                result.append(Component.literal(visibleName.substring(cursor, match.range.first)).withStyle(original.style))
            }
            result.append(protectedNameComponent(alias, original.style))
            cursor = match.range.last + 1
        }
        if (cursor < visibleName.length) {
            result.append(Component.literal(visibleName.substring(cursor)).withStyle(original.style))
        }
        return result
    }

    @JvmStatic
    fun protectedNameComponent(name: String, baseStyle: Style): Component {
        if (!colorfulName.value || name.isEmpty()) {
            return Component.literal(name).withStyle(baseStyle)
        }
        val result = Component.literal("").withStyle(baseStyle)
        name.codePoints().toArray().forEachIndexed { index, codePoint ->
            val rgb = HUDModule.sampleColor(index * COLOR_OFFSET).toArgb() and 0xFFFFFF
            result.append(
                Component.literal(String(Character.toChars(codePoint)))
                    .withStyle(baseStyle.withColor(rgb))
            )
        }
        return result
    }

    fun nameColorSpans(name: String, baseColor: NanoVGColor): List<TextSpan> {
        if (!colorfulName.value || name.isEmpty()) return listOf(TextSpan(name, baseColor))
        return name.codePoints().toArray().mapIndexed { index, codePoint ->
            TextSpan(
                String(Character.toChars(codePoint)),
                HUDModule.sampleColor(index * COLOR_OFFSET).multiplyAlpha(baseColor.a)
            )
        }
    }

    @JvmStatic
    fun protectChatSequence(message: FormattedCharSequence): FormattedCharSequence {
        val names = chatNames() ?: return message
        val accountName = names.first
        val alias = names.second
        val glyphs = mutableListOf<StyledCodePoint>()
        val text = StringBuilder()
        message.accept { _, style, codePoint ->
            glyphs += StyledCodePoint(style, codePoint)
            text.appendCodePoint(codePoint)
            true
        }

        val matches = namePattern(accountName).findAll(text).toList()
        if (matches.isEmpty()) return message

        val glyphAtOffset = HashMap<Int, Int>(glyphs.size + 1)
        var sourceOffset = 0
        glyphs.forEachIndexed { index, glyph ->
            glyphAtOffset[sourceOffset] = index
            sourceOffset += Character.charCount(glyph.codePoint)
        }
        glyphAtOffset[sourceOffset] = glyphs.size

        val matchAtOffset = matches.mapNotNull { match ->
            val start = glyphAtOffset[match.range.first]
            val end = glyphAtOffset[match.range.last + 1]
            if (start == null || end == null || start >= end) null else start to end
        }.toMap()
        if (matchAtOffset.isEmpty()) return message

        val output = mutableListOf<StyledCodePoint>()
        var glyphIndex = 0
        while (glyphIndex < glyphs.size) {
            val matchEnd = matchAtOffset[glyphIndex]
            if (matchEnd != null) {
                val style = glyphs[glyphIndex].style
                alias.codePoints().toArray().forEachIndexed { index, codePoint ->
                    val replacementStyle = if (colorfulName.value) {
                        style.withColor(HUDModule.sampleColor(index * COLOR_OFFSET).toArgb() and 0xFFFFFF)
                    } else {
                        style
                    }
                    output += StyledCodePoint(replacementStyle, codePoint)
                }
                glyphIndex = matchEnd
            } else {
                output += glyphs[glyphIndex]
                glyphIndex++
            }
        }

        return FormattedCharSequence { sink ->
            var outputOffset = 0
            output.all { glyph ->
                val keepGoing = sink.accept(outputOffset, glyph.style, glyph.codePoint)
                outputOffset += Character.charCount(glyph.codePoint)
                keepGoing
            }
        }
    }

    private fun chatNames(): Pair<String, String>? {
        val player = Minecraft.getInstance().player ?: return null
        val alias = replacementForPlayer(player.uuid) ?: return null
        val accountName = player.gameProfile.name
        if (accountName.isBlank() || accountName == alias) return null
        return accountName to alias
    }

    private fun namePattern(accountName: String): Regex =
        Regex("(?<![A-Za-z0-9_])${Regex.escape(accountName)}(?![A-Za-z0-9_])")

    private data class StyledCodePoint(val style: Style, val codePoint: Int)

    private const val COLOR_OFFSET = 0.12f
}
