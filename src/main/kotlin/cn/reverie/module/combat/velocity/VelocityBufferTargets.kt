package cn.reverie.module.combat.velocity

import cn.reverie.module.combat.backtrack.BacktrackPacketClassifier
import cn.reverie.module.combat.backtrack.BacktrackReachMath
import cn.reverie.module.combat.backtrack.BacktrackTracker
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

/** Tracks server-reported entity positions while Buffer mode withholds inbound packets. */
internal object VelocityBufferTargets {
    private data class Estimate(val position: Vec3, val movement: Vec3)

    private val positionTracker = BacktrackTracker()
    private val estimates = ConcurrentHashMap<Int, Estimate>()
    private val removedIds = ConcurrentHashMap.newKeySet<Int>()

    @Synchronized
    fun reset() {
        positionTracker.reset()
        estimates.clear()
        removedIds.clear()
    }

    @Synchronized
    fun observe(packet: Packet<*>, level: Level, localPlayerId: Int) {
        for (heldPacket in BacktrackPacketClassifier.unwrap(packet)) {
            if (heldPacket is ClientboundRemoveEntitiesPacket) {
                val removed = heldPacket.entityIds.toList()
                for (id in removed) {
                    positionTracker.forget(id)
                    estimates.remove(id)
                    removedIds.add(id)
                }
                continue
            }

            if (heldPacket is ClientboundAddEntityPacket) {
                removedIds.remove(heldPacket.id)
            }

            val entityId = BacktrackPacketClassifier.relatedEntityIds(heldPacket, level).firstOrNull() ?: continue
            if (entityId == localPlayerId || entityId in removedIds) continue
            val entity = level.getEntity(entityId) ?: continue
            val current = estimates[entityId]?.position ?: entity.position()
            val next = positionTracker.applyPacketToPosition(entity, current, heldPacket) ?: continue

            positionTracker.noteServerPosition(entityId, next)
            estimates[entityId] = Estimate(next, next.subtract(current))
        }
    }

    fun position(entity: Entity): Vec3? = estimates[entity.id]?.position

    fun hitbox(entity: Entity): AABB? = position(entity)?.let { BacktrackReachMath.boxFromPosition(entity, it) }

    fun movement(entity: Entity): Vec3? = estimates[entity.id]?.movement

    fun isRemoved(entity: Entity): Boolean = entity.id in removedIds
}
