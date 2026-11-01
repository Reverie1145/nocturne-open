package cn.reverie.module.combat

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/** Filters common NPC/fake-player entities from automated combat targeting. */
object AntiBot : Module(
    name = "AntiBot",
    description = "Filters NPCs and fake players from combat targeting",
    category = Category.COMBAT
) {

    val tabCheck = BooleanSetting(
        "Tab Check",
        "Ignore player entities that are not listed in the tab list",
        true
    )
    val npcNameCheck = BooleanSetting(
        "NPC Name Check",
        "Ignore player entities whose name contains NPC, Bot, or FakePlayer",
        false
    )

    @JvmStatic
    fun isBot(entity: Entity?): Boolean {
        if (!enabled || entity !is Player) return false

        val mc = Minecraft.getInstance()
        if (tabCheck.value) {
            // Fail open when there is no active connection; otherwise an absent
            // player-info entry is a useful signal for NPC/fake-player entities.
            val connection = mc.connection ?: return false
            if (connection.getPlayerInfo(entity.uuid) == null) return true
        }

        if (npcNameCheck.value) {
            val profileName = entity.gameProfile.name
            val displayName = entity.displayName.string
            if (isNpcName(profileName) || isNpcName(displayName)) return true
        }

        return false
    }

    private fun isNpcName(name: String): Boolean {
        val plainName = name.replace(FORMATTING_CODE, "")
        return NPC_MARKER.containsMatchIn(plainName)
    }

    private val FORMATTING_CODE = Regex("(?i)§[0-9a-fk-or]")
    private val NPC_MARKER = Regex("(?i)(^|[^a-z0-9])(npc|bot|fakeplayer)([^a-z0-9]|$)")
}
