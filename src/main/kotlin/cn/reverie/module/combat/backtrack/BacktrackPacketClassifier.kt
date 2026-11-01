package cn.reverie.module.combat.backtrack

import cn.reverie.mixin.network.ClientboundMoveEntityPacketAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundAnimatePacket
import net.minecraft.network.protocol.game.ClientboundBundlePacket
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundLoginPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

object BacktrackPacketClassifier {
    enum class Kind { PASS, DELAY, FLUSH }

    data class Classification(
        val kind: Kind,
        val reason: BacktrackFlushReason? = null,
        val relatedEntityIds: List<Int> = emptyList()
    ) {
        val isDelay: Boolean get() = kind == Kind.DELAY
        val isFlush: Boolean get() = kind == Kind.FLUSH
        val isPass: Boolean get() = kind == Kind.PASS
    }

    fun unwrap(packet: Packet<*>): List<Packet<*>> {
        val result = ArrayList<Packet<*>>()
        unwrapInto(packet, result)
        return result
    }

    private fun unwrapInto(packet: Packet<*>, out: MutableList<Packet<*>>) {
        if (packet is ClientboundBundlePacket) {
            for (child in packet.subPackets()) unwrapInto(child, out)
        } else {
            out += packet
        }
    }

    fun classify(packet: Packet<*>, mc: Minecraft, trackedTargetId: Int, playersOnly: Boolean): Classification {
        if (packet is ClientboundDisconnectPacket) return Classification(Kind.FLUSH, BacktrackFlushReason.DISCONNECT)
        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundRespawnPacket || packet is ClientboundLoginPacket) {
            return Classification(Kind.FLUSH, BacktrackFlushReason.SETBACK)
        }
        if (packet is ClientboundExplodePacket) return Classification(Kind.FLUSH, BacktrackFlushReason.EXPLOSION)

        if (packet is ClientboundRemoveEntitiesPacket) {
            val ids = packet.entityIds.toList()
            if (trackedTargetId != NO_TARGET_ID && trackedTargetId in ids) {
                return Classification(Kind.FLUSH, BacktrackFlushReason.TARGET_DESTROYED, ids)
            }
            return Classification(Kind.PASS)
        }

        if (packet is ClientboundTeleportEntityPacket && isLargeTeleport(mc.level, packet)) {
            if (trackedTargetId != NO_TARGET_ID && packet.id() == trackedTargetId) {
                return Classification(
                    Kind.FLUSH,
                    BacktrackFlushReason.TARGET_TELEPORT,
                    listOf(packet.id())
                )
            }
            return Classification(Kind.PASS)
        }

        if (!isDelayableEntityPacket(packet, mc, trackedTargetId, playersOnly)) return Classification(Kind.PASS)
        val related = relatedEntityIds(packet, mc.level)
        return if (related.isEmpty()) Classification(Kind.PASS) else Classification(Kind.DELAY, relatedEntityIds = related)
    }

    fun isDelayableEntityPacket(packet: Packet<*>, mc: Minecraft, trackedTargetId: Int, playersOnly: Boolean): Boolean {
        if (packet !is ClientboundMoveEntityPacket &&
            packet !is ClientboundEntityPositionSyncPacket &&
            packet !is ClientboundTeleportEntityPacket &&
            packet !is ClientboundSetEntityDataPacket &&
            packet !is ClientboundUpdateAttributesPacket
        ) return false

        val id = relatedEntityIds(packet, mc.level).firstOrNull() ?: return false
        if (trackedTargetId != NO_TARGET_ID) return id == trackedTargetId
        return shouldTrackEntity(mc.level, mc.player?.id, id, playersOnly)
    }

    fun isPositionPacket(packet: Packet<*>): Boolean = when (packet) {
        is ClientboundMoveEntityPacket -> packet.hasPosition()
        is ClientboundEntityPositionSyncPacket, is ClientboundTeleportEntityPacket -> true
        else -> false
    }

    fun relatedEntityIds(packet: Packet<*>, level: Level? = Minecraft.getInstance().level): List<Int> {
        return when (packet) {
            is ClientboundMoveEntityPacket -> listOf((packet as ClientboundMoveEntityPacketAccessor).nocturneGetEntityId())
            is ClientboundEntityPositionSyncPacket -> listOf(packet.id())
            is ClientboundTeleportEntityPacket -> listOf(packet.id())
            is ClientboundSetEntityDataPacket -> listOf(packet.id())
            is ClientboundSetEquipmentPacket -> listOf(packet.entity)
            is ClientboundUpdateAttributesPacket -> listOf(packet.getEntityId())
            is ClientboundAddEntityPacket -> listOf(packet.id)
            is ClientboundRemoveEntitiesPacket -> packet.entityIds.toList()
            is ClientboundRotateHeadPacket -> {
                val currentLevel = level ?: return emptyList()
                packet.getEntity(currentLevel)?.let { listOf(it.id) } ?: emptyList()
            }
            is ClientboundSetPassengersPacket -> listOf(packet.vehicle)
            is ClientboundEntityEventPacket -> {
                val currentLevel = level ?: return emptyList()
                packet.getEntity(currentLevel)?.let { listOf(it.id) } ?: emptyList()
            }
            is ClientboundAnimatePacket -> listOf(packet.id)
            is ClientboundUpdateMobEffectPacket -> listOf(packet.entityId)
            is ClientboundRemoveMobEffectPacket -> listOf(packet.entityId())
            is ClientboundDamageEventPacket -> listOf(packet.entityId())
            is ClientboundSetEntityLinkPacket -> listOf(packet.sourceId, packet.destId)
            is ClientboundSetEntityMotionPacket -> listOf(packet.id())
            is ClientboundSoundEntityPacket -> listOf(packet.id)
            else -> emptyList()
        }
    }

    private fun isLargeTeleport(level: ClientLevel?, packet: ClientboundTeleportEntityPacket): Boolean {
        val entity = level?.getEntity(packet.id()) ?: return false
        val next = packet.change().position()
        return entity.position().distanceToSqr(next) > LARGE_TELEPORT_DISTANCE_SQUARED
    }

    private fun shouldTrackEntity(level: Level?, localPlayerId: Int?, entityId: Int, playersOnly: Boolean): Boolean {
        if (entityId == localPlayerId || level == null) return false
        val entity: Entity = level.getEntity(entityId) ?: return false
        if (entity !is LivingEntity) return false
        return !playersOnly || entity is Player
    }

    const val NO_TARGET_ID: Int = Int.MIN_VALUE
    private const val LARGE_TELEPORT_DISTANCE_SQUARED = 64.0
}
