package cn.reverie.render.hud.impl

import cn.reverie.module.BooleanSetting
import cn.reverie.render.core.RenderEngine
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.font.FontRenderer
import cn.reverie.render.font.TextAlignment
import cn.reverie.render.hud.HUDModule
import cn.reverie.render.hud.HudElement
import cn.reverie.render.hud.HudHorizontalAnchor
import cn.reverie.render.hud.HudVerticalAnchor
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.numbers.StyledFormat
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam

/** Styled replacement for Minecraft's sidebar scoreboard, using its original objective and row rules. */
object ScoreboardWidget : HudElement(
    name = "Scoreboard",
    defaultX = 0f,
    defaultY = 74f,
    width = 150f,
    height = 54f,
    defaultEnabled = true,
    horizontalAnchor = HudHorizontalAnchor.RIGHT,
    verticalAnchor = HudVerticalAnchor.TOP
) {
    val showScores = BooleanSetting("Show Scores", "Display the objective value at the right of each row", true)

    init {
        addSetting(showScores)
        x = (ResolutionHelper.scaledWidth - width - 8f).coerceAtLeast(8f)
    }

    override fun resetPosition() {
        x = (ResolutionHelper.scaledWidth - width * scale - 8f).coerceAtLeast(8f)
        y = 74f
    }

    private data class Row(val name: String, val score: String, val scoreWidth: Float)
    private data class Content(val title: String, val rows: List<Row>, val width: Float)

    /** Convert Minecraft component styles to legacy color codes understood by FontRenderer. */
    private fun formatted(component: Component): String {
        val result = StringBuilder()
        component.visit({ style, text ->
            result.append("§r")
            style.color?.let { color ->
                result.append("§#").append(color.value.toString(16).padStart(6, '0').takeLast(6))
            }
            if (style.isBold) result.append("§l")
            if (style.isItalic) result.append("§o")
            if (style.isUnderlined) result.append("§n")
            if (style.isStrikethrough) result.append("§m")
            result.append(text)
            java.util.Optional.empty<Void>()
        }, Style.EMPTY)
        return result.toString()
    }

    private fun objective(): Objective? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        val scoreboard = level.scoreboard
        val team = scoreboard.getPlayersTeam(player.scoreboardName)
        val teamObjective = team?.color?.map { scoreboard.getDisplayObjective(it.displaySlot()) }?.orElse(null)
        return teamObjective ?: scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }

    private fun content(isEditing: Boolean, font: FontRenderer): Content? {
        val objective = objective()
        if (objective == null) {
            if (!isEditing) return null
            val sample = listOf("Nocturne", "Scoreboard preview", "Player")
            val rows = sample.mapIndexed { index, name ->
                val score = listOf("120", "64", "12")[index]
                Row(name, score, font.getStringWidth(score, 8.5f))
            }
            val title = "Server Objective"
            val contentWidth = maxOf(font.getStringWidth(title, 9f), rows.maxOf { font.getStringWidth(it.name, 8.5f) + 30f })
            return Content(title, rows, contentWidth)
        }

        val scoreboard = objective.scoreboard
        val scoreFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT)
        val rows = scoreboard.listPlayerScores(objective)
            .asSequence()
            .filterNot(PlayerScoreEntry::isHidden)
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.owner })
            .take(15)
            .map { score ->
                val team = scoreboard.getPlayersTeam(score.owner)
                val name = formatted(PlayerTeam.formatNameForTeam(team, score.ownerName()))
                val value = formatted(score.formatValue(scoreFormat))
                Row(name, value, font.getStringWidth(value, 8.5f))
            }
            .toList()
        val title = formatted(objective.displayName)
        val titleWidth = font.getStringWidth(title, 9f)
        val rowsWidth = rows.maxOfOrNull { row ->
            font.getStringWidth(row.name, 8.5f) + if (showScores.value) 12f + row.scoreWidth else 0f
        } ?: 0f
        return Content(title, rows, maxOf(titleWidth, rowsWidth))
    }

    override fun render(partialTicks: Float, isEditing: Boolean) {
        if (!enabled && !isEditing) return
        val font = RenderEngine.fontRenderer
        val content = content(isEditing, font)
        if (content == null) {
            width = 150f
            height = 22f
            if (isEditing) renderEditBoundingBox(partialTicks)
            return
        }

        val padX = 8f
        val padY = 6f
        val titleHeight = 13f
        val rowHeight = 11f
        val rowsHeight = content.rows.size * rowHeight
        width = maxOf(112f, content.width + padX * 2f)
        height = padY + titleHeight + (if (content.rows.isNotEmpty()) 4f + rowsHeight else 0f) + padY

        val radius = 6f
        drawWidgetShadow(x, y, width, height, radius, 0.42f, blur = 9f, colorOverride = NanoVGColor(0f, 0f, 0f, 1f))
        drawBackdrop(x, y, width, height, radius, NanoVGColor(0.035f, 0.035f, 0.035f, bgOpacity))
        NanoVGRenderer.drawRoundedOutline(x, y, width, height, radius, NanoVGColor(1f, 1f, 1f, 0.075f), 0.75f)

        font.drawString(content.title, x + padX, y + padY, NanoVGColor(1f, 1f, 1f, 0.98f), 9f, shadow = HUDModule.textShadow.value)

        if (content.rows.isNotEmpty()) {
            val dividerY = y + padY + titleHeight + 1f
            NanoVGRenderer.drawRoundedRect(x + padX, dividerY, width - padX * 2f, 0.7f, 0.35f, NanoVGColor(1f, 1f, 1f, 0.11f))
            content.rows.forEachIndexed { index, row ->
                val rowY = dividerY + 4f + index * rowHeight
                val textColor = if (index == 0) NanoVGColor(0.96f, 0.96f, 0.96f, 0.96f) else NanoVGColor(0.84f, 0.84f, 0.84f, 0.92f)
                font.drawString(row.name, x + padX, rowY, textColor, 8.5f, shadow = HUDModule.textShadow.value)
                if (showScores.value) {
                    font.drawString(row.score, x + width - padX, rowY, NanoVGColor(0.9f, 0.9f, 0.9f, 0.96f), 8.5f, TextAlignment.TOP_RIGHT, HUDModule.textShadow.value)
                }
            }
        }

        if (isEditing) renderEditBoundingBox(partialTicks)
    }
}
