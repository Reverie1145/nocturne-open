package cn.reverie.module.combat.backtrack

import cn.reverie.mixin.network.ClientboundMoveEntityPacketAccessor
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.PositionMoveRotation
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

class BacktrackTracker {
    private val serverPositions = ConcurrentHashMap<Int, Vec3>()
    private val serverHitboxes = ConcurrentHashMap<Int, AABB>()

    @Volatile
    var currentTargetId: Int = BacktrackPacketClassifier.NO_TARGET_ID
        private set

    fun reset() {
        serverPositions.clear()
        serverHitboxes.clear()
        currentTargetId = BacktrackPacketClassifier.NO_TARGET_ID
    }

    fun setCurrentTarget(entityId: Int) {
        currentTargetId = entityId
    }

    fun captureApplied(entity: Entity?) {
        if (entity == null) return
        serverPositions[entity.id] = entity.position()
        serverHitboxes[entity.id] = entity.boundingBox
    }

    fun noteServerPosition(entityId: Int, position: Vec3, hitbox: AABB? = null) {
        serverPositions[entityId] = position
        if (hitbox != null) serverHitboxes[entityId] = hitbox
    }

    fun getServerPosition(entityId: Int): Vec3? = serverPositions[entityId]

    fun getServerHitbox(entityId: Int, fallback: Entity? = null): AABB? {
        serverHitboxes[entityId]?.let { return it }
        val position = serverPositions[entityId]
        if (position != null && fallback != null) return BacktrackReachMath.boxFromPosition(fallback, position)
        return fallback?.boundingBox
    }

    fun forget(entityId: Int) {
        serverPositions.remove(entityId)
        serverHitboxes.remove(entityId)
        if (currentTargetId == entityId) currentTargetId = BacktrackPacketClassifier.NO_TARGET_ID
    }

    fun reconstruct(entityId: Int, heldPackets: List<BacktrackPacketQueue.HeldPacket>): List<BacktrackPosition> {
        val level = Minecraft.getInstance().level ?: return emptyList()
        val entity = level.getEntity(entityId) ?: return emptyList()
        var cursor = entity.position()
        var cursorBox = entity.boundingBox
        val now = System.currentTimeMillis()
        val frames = ArrayList<BacktrackPosition>(heldPackets.size + 1)
        frames += BacktrackPosition(entityId, cursor, cursorBox, now, -1)

        for ((index, held) in heldPackets.withIndex()) {
            val next = applyPacketToPosition(entity, cursor, held.packet) ?: continue
            cursor = next
            cursorBox = BacktrackReachMath.boxFromPosition(entity, cursor)
            frames += BacktrackPosition(entityId, cursor, cursorBox, held.receiveTime, index)
            noteServerPosition(entityId, cursor, cursorBox)
        }
        return frames
    }

    fun applyPacketToPosition(entity: Entity, current: Vec3, packet: Packet<*>): Vec3? {
        when (packet) {
            is ClientboundMoveEntityPacket -> {
                val packetEntityId = (packet as ClientboundMoveEntityPacketAccessor).nocturneGetEntityId()
                if (packetEntityId != entity.id || !packet.hasPosition()) return null
                return Vec3(
                    unpackCoordinate(current.x, packet.getXa()),
                    unpackCoordinate(current.y, packet.getYa()),
                    unpackCoordinate(current.z, packet.getZa())
                )
            }
            is ClientboundEntityPositionSyncPacket -> {
                if (packet.id() != entity.id) return null
                return packet.values().position()
            }
            is ClientboundTeleportEntityPacket -> {
                if (packet.id() != entity.id) return null
                val base = PositionMoveRotation(current, entity.deltaMovement, entity.yRot, entity.xRot)
                return PositionMoveRotation.calculateAbsolute(base, packet.change(), packet.relatives()).position()
            }
            is ClientboundAddEntityPacket -> {
                if (packet.id != entity.id) return null
                return Vec3(packet.x, packet.y, packet.z)
            }
            else -> return null
        }
    }

    private fun unpackCoordinate(current: Double, delta: Short): Double {
        if (delta == 0.toShort()) return current
        return (Math.round(current * 4096.0) + delta) / 4096.0
    }
}
