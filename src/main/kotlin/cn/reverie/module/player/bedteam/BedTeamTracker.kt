package cn.reverie.module.player.bedteam

import cn.reverie.module.player.bedbreaker.BedTeamChatLog
import cn.reverie.module.player.bedbreaker.BedTeamProtection
import cn.reverie.util.misc.ChatUtil
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Shared helmet identification and English client-chat diagnostics for both bed modules. */
internal class BedTeamTracker(private val moduleName: String, private val action: Action) {
    private val protection = BedTeamProtection(DyeColor.values().associateWith {
        listOf(it.textureDiffuseColor, it.fireworkColor, it.textColor)
    })
    private val chatLog = BedTeamChatLog()
    private val lastActionMessages = mutableMapOf<String, String>()

    val ready: Boolean get() = protection.readyToMine
    val protectedColor: DyeColor? get() = protection.protectedColor

    fun observeHelmet(player: LocalPlayer) {
        protection.observeHelmetColor(readHelmetRgb(player))
    }

    fun matchesCurrentHelmet(player: LocalPlayer): Boolean =
        ready && readHelmetRgb(player) == protection.helmetRgb

    fun isOwnBed(color: DyeColor): Boolean = ready && color == protectedColor

    fun isProtectedBed(color: DyeColor): Boolean = protection.isProtectedBed(color)

    fun canMine(sourceBedColor: DyeColor?, targetBedColor: DyeColor?): Boolean =
        protection.canMine(sourceBedColor, targetBedColor)

    fun reset() {
        protection.reset()
        chatLog.reset()
        lastActionMessages.clear()
    }

    fun logNearbyBeds(player: LocalPlayer, level: ClientLevel, range: Double, teamCheck: Boolean, enabled: Boolean) {
        if (!enabled) {
            chatLog.reset()
            lastActionMessages.clear()
            return
        }
        if (!chatLog.shouldSample(System.nanoTime() / 1_000_000L)) return

        val helmet = player.getItemBySlot(EquipmentSlot.HEAD)
        val match = protection.identification
        val state = LogState(
            BuiltInRegistries.ITEM.getKey(helmet.item).toString(), protection.helmetRgb,
            match?.color, protectedColor, match?.exact == true, ready, teamCheck, range
        )
        val operation = if (action == Action.BREAK) "mining" else "defense"
        val stateMessages = when {
            !teamCheck -> listOf("TeamCheck disabled; $operation is not filtered by team color.")
            match == null -> listOf("Helmet=${state.helmetItem}; RGB=unavailable; own bed unknown; $operation paused.")
            else -> {
                val method = if (match.exact) "exact standard color match" else "nearest color inference"
                val status = if (ready) "confirmed; team check active" else "confirmation pending; $operation paused"
                listOf(
                    "Helmet=${state.helmetItem}; RGB=${formatRgb(state.rgb)}; unique own-bed color=${describeColor(match.color)}; $method; $status.",
                    "Color candidates: " + match.candidates.joinToString("; ") {
                        "${describeColor(it.color)} DeltaE=${String.format(Locale.ROOT, "%.2f", it.distance)}"
                    }
                )
            }
        }
        val nearby = if (teamCheck) scanNearbyBeds(player, level, range) else emptyList()
        val ownColor = if (ready) state.confirmed else state.detected
        val ownCount = nearby.count { it.color == ownColor }
        val summary = when {
            !teamCheck -> "Team color filtering is disabled for automatic $operation."
            ownColor == null -> "Range=$range; nearby beds=${nearby.size}; own bed awaiting identification."
            else -> "Range=$range; nearby beds=${nearby.size}; ${if (ready) "own beds" else "own-bed candidates"}=$ownCount (${describeColor(ownColor)})."
        }
        val bedMessages = nearby.associate { bed ->
            val verdict = when {
                bed.color == ownColor && ready -> if (action == Action.BREAK) {
                    "OWN BED: head and foot protected"
                } else "OWN BED: eligible for defense"
                bed.color == ownColor -> "OWN BED CANDIDATE: confirmation pending; $operation paused"
                !ready -> "OTHER BED: waiting for helmet confirmation; $operation paused"
                action == Action.BREAK -> "ENEMY BED: mining allowed by team check"
                else -> "ENEMY BED: skipped by defense"
            }
            formatPos(bed.footPos) to "${describeColor(bed.color)} bed @${formatPos(bed.footPos)} -> $verdict."
        }
        for (message in chatLog.changes(state.toString(), stateMessages, summary, bedMessages)) send(message)
    }

    fun logMiningTarget(level: ClientLevel, sourceBedPos: BlockPos?, targetPos: BlockPos, teamCheck: Boolean, enabled: Boolean) {
        if (!teamCheck || !enabled) return
        val target = level.getBlockState(targetPos)
        val targetBed = target.block as? BedBlock
        val sourceBed = sourceBedPos?.let { level.getBlockState(it).block } as? BedBlock
        val description = if (targetBed != null) "${describeColor(targetBed.color)} bed" else {
            "covering block ${BuiltInRegistries.BLOCK.getKey(target.block)}"
        }
        logAction("mining", "Mining attempt: $description @${formatPos(targetPos)}; source bed color=${describeColor(sourceBed?.color)}; own-bed color=${describeColor(protectedColor)}; helmet RGB=${formatRgb(protection.helmetRgb)}.")
    }

    fun logDefenseBed(level: ClientLevel, bedPos: BlockPos, teamCheck: Boolean, enabled: Boolean) {
        if (!enabled) return
        val bed = level.getBlockState(bedPos).block as? BedBlock ?: return
        val selection = if (teamCheck) "own" else "unfiltered"
        logAction("defense bed", "Selected $selection bed: ${describeColor(bed.color)} @${formatPos(bedPos)}; own-bed color=${describeColor(protectedColor)}; helmet RGB=${formatRgb(protection.helmetRgb)}.")
    }

    fun logPlacementTarget(level: ClientLevel, player: LocalPlayer, bedPos: BlockPos, destination: BlockPos, hand: InteractionHand, enabled: Boolean) {
        if (!enabled) return
        val bed = level.getBlockState(bedPos).block as? BedBlock ?: return
        val block = (player.getItemInHand(hand).item as? BlockItem)?.block ?: return
        logAction("placement", "Placement attempt: ${BuiltInRegistries.BLOCK.getKey(block)} @${formatPos(destination)}; defending ${describeColor(bed.color)} bed @${formatPos(bedPos)}; own-bed color=${describeColor(protectedColor)}; helmet RGB=${formatRgb(protection.helmetRgb)}.")
    }

    private fun scanNearbyBeds(player: LocalPlayer, level: ClientLevel, range: Double): List<NearbyBed> {
        val radius = ceil(range).toInt()
        val maxDistanceSq = range * range
        val center = player.blockPosition()
        val origin = if (action == Action.BREAK) player.eyePosition else player.position()
        val beds = linkedMapOf<BlockPos, NearbyBed>()
        for (x in center.x - radius..center.x + radius) for (z in center.z - radius..center.z + radius) {
            val chunk = level.chunkSource.getChunkNow(x shr 4, z shr 4) ?: continue
            for (y in max(level.minY, center.y - radius)..min(level.maxY - 1, center.y + radius)) {
                val pos = BlockPos(x, y, z)
                if (origin.distanceToSqr(Vec3.atCenterOf(pos)) > maxDistanceSq) continue
                val state = chunk.getBlockState(pos)
                val bed = state.block as? BedBlock ?: continue
                val foot = if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
                    pos.relative(state.getValue(HorizontalDirectionalBlock.FACING).opposite)
                } else pos
                if (action == Action.DEFEND && origin.distanceToSqr(Vec3.atCenterOf(foot)) > maxDistanceSq) continue
                beds[foot] = NearbyBed(foot, bed.color)
            }
        }
        return beds.values.sortedWith(compareBy({ it.footPos.x }, { it.footPos.y }, { it.footPos.z }))
    }

    private fun readHelmetRgb(player: LocalPlayer): Int? =
        player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.DYED_COLOR)?.rgb?.and(0xFFFFFF)

    private fun logAction(key: String, message: String) {
        if (lastActionMessages[key] == message) return
        lastActionMessages[key] = message
        send(message)
    }

    private fun send(message: String) = ChatUtil.sendInfo("[$moduleName/TeamCheck] $message")

    private fun formatRgb(rgb: Int?): String = if (rgb == null) "unavailable" else String.format(Locale.ROOT, "#%06X", rgb and 0xFFFFFF)

    private fun formatPos(pos: BlockPos): String = "(${pos.x}, ${pos.y}, ${pos.z})"

    private fun describeColor(color: DyeColor?): String = color?.name ?: "unknown"

    enum class Action { BREAK, DEFEND }

    private data class NearbyBed(val footPos: BlockPos, val color: DyeColor)

    private data class LogState(
        val helmetItem: String, val rgb: Int?, val detected: DyeColor?, val confirmed: DyeColor?,
        val exact: Boolean, val ready: Boolean, val enabled: Boolean, val range: Double
    )
}
