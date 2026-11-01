package cn.reverie.module.player

import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import org.lwjgl.glfw.GLFW

object Teams : Module(
    name = "Teams",
    description = "Filter and distinguish teammates",
    category = Category.PLAYER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {

    val scoreboard = BooleanSetting("Scoreboard", "Check scoreboard team alliance", true)
    val armorColor = BooleanSetting("Armor Color", "Check dyed leather armor color on all armor pieces", true)
    val nameColor = BooleanSetting("Name Color", "Check display name style color", true)
    val colorCode = BooleanSetting("Color Code", "Check § formatting color code", true)
    val prefix = BooleanSetting("Prefix", "Check [Team] prefix in display name", false)

    override val suffix: String
        get() = when {
            scoreboard.value && colorCode.value -> "Scoreboard & Color"
            scoreboard.value -> "Scoreboard"
            armorColor.value -> "Armor"
            colorCode.value -> "Color"
            prefix.value -> "Prefix"
            else -> ""
        }

    @JvmStatic
    fun isTeammate(entity: Entity?): Boolean {
        if (!enabled || entity == null) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (entity === player) return true
        if (entity !is Player) return false

        // 1. Scoreboard team check
        if (scoreboard.value) {
            if (player.isAlliedTo(entity)) return true
            val myTeam = player.team
            val theirTeam = entity.team
            if (myTeam != null && theirTeam != null && myTeam.name == theirTeam.name) {
                return true
            }
        }

        // 2. Armor color check (ALL 4 slots: HEAD, CHEST, LEGS, FEET, exactly like Hanabi!)
        if (armorColor.value) {
            val slots = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
            for (slot in slots) {
                val myArmor = player.getItemBySlot(slot)
                val otherArmor = entity.getItemBySlot(slot)
                if (!myArmor.isEmpty && !otherArmor.isEmpty) {
                    val myDye = myArmor.get(DataComponents.DYED_COLOR)?.rgb ?: -1
                    val otherDye = otherArmor.get(DataComponents.DYED_COLOR)?.rgb ?: -1
                    if (myDye != -1 && otherDye != -1 && myDye == otherDye) {
                        return true
                    }
                }
            }
        }

        val clientDisplay = player.displayName
        val targetDisplay = entity.displayName
        if (clientDisplay != null && targetDisplay != null) {
            // 3. Name Color (Style color)
            if (nameColor.value) {
                val clientColor = clientDisplay.style.color
                val targetColor = targetDisplay.style.color
                if (clientColor != null && targetColor != null && clientColor == targetColor) {
                    return true
                }
            }

            // 4. Color Code (§ character comparison, exactly like Hanabi!)
            if (colorCode.value) {
                val clientStr = clientDisplay.string.replace("§r", "")
                val targetStr = targetDisplay.string.replace("§r", "")
                val clientCodeIdx = clientStr.indexOf('§')
                val targetCodeIdx = targetStr.indexOf('§')
                if (clientCodeIdx != -1 && targetCodeIdx != -1 &&
                    clientCodeIdx + 1 < clientStr.length && targetCodeIdx + 1 < targetStr.length) {
                    val clientChar = clientStr[clientCodeIdx + 1].lowercaseChar()
                    val targetChar = targetStr[targetCodeIdx + 1].lowercaseChar()
                    if (clientChar == targetChar) {
                        return true
                    }
                }
            }

            // 5. Prefix check ([RED] vs [RED], exactly like Hanabi!)
            if (prefix.value) {
                val clientRaw = stripFormatting(clientDisplay.string).trim()
                val targetRaw = stripFormatting(targetDisplay.string).trim()
                if (clientRaw.startsWith("[") && targetRaw.startsWith("[")) {
                    val clientEnd = clientRaw.indexOf(']')
                    val targetEnd = targetRaw.indexOf(']')
                    if (clientEnd > 1 && targetEnd > 1) {
                        val clientPrefix = clientRaw.substring(0, clientEnd + 1)
                        val targetPrefix = targetRaw.substring(0, targetEnd + 1)
                        if (clientPrefix.equals(targetPrefix, ignoreCase = true)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun stripFormatting(input: String?): String {
        return input?.replace(Regex("(?i)§[0-9a-fk-or]"), "") ?: ""
    }
}
