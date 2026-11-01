package cn.reverie.module.combat.backtrack

import cn.reverie.util.rotation.RotationUtil
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.component.AttackRange
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth
import kotlin.math.min

object BacktrackReachMath {
    private const val GRIM_REACH_THRESHOLD = 0.0005
    private const val GRIM_MOVEMENT_THRESHOLD = 0.0002
    private const val VANILLA_ENTITY_RANGE = 3.0

    fun grimHitboxMargin(player: LivingEntity): Double =
        GRIM_REACH_THRESHOLD + GRIM_MOVEMENT_THRESHOLD + (resolveAttackRange(player)?.hitboxMargin()?.toDouble() ?: 0.0)

    fun grimMaxReach(player: LivingEntity, configuredRange: Double): Double {
        val vanilla = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE)
        val itemRange = resolveAttackRange(player)?.effectiveMaxRange(player)?.toDouble() ?: vanilla
        return min(configuredRange, min(vanilla, itemRange))
    }

    fun resolveAttackRange(entity: LivingEntity): AttackRange? =
        entity.mainHandItem.get(DataComponents.ATTACK_RANGE)

    fun expandForGrim(box: AABB, player: LivingEntity): AABB = box.inflate(grimHitboxMargin(player))

    fun nearestPoint(eye: Vec3, box: AABB): Vec3 = Vec3(
        Mth.clamp(eye.x, box.minX, box.maxX),
        Mth.clamp(eye.y, box.minY, box.maxY),
        Mth.clamp(eye.z, box.minZ, box.maxZ)
    )

    fun minDistanceToBox(eye: Vec3, box: AABB): Double =
        if (box.contains(eye)) 0.0 else eye.distanceTo(nearestPoint(eye, box))

    fun isKnownInvalid(player: LivingEntity, rawBox: AABB, configuredRange: Double): Boolean =
        minDistanceToBox(player.getEyePosition(), expandForGrim(rawBox, player)) > grimMaxReach(player, configuredRange)

    private fun intercept(box: AABB, start: Vec3, end: Vec3): Vec3? =
        if (box.contains(start)) start else box.clip(start, end).orElse(null)

    fun tracedDistance(player: LivingEntity, rawBox: AABB, configuredRange: Double): Double {
        val box = expandForGrim(rawBox, player)
        val maxReach = grimMaxReach(player, configuredRange)
        val traceLength = maxReach + VANILLA_ENTITY_RANGE
        val eye = player.getEyePosition()
        if (box.contains(eye)) return 0.0

        var minimum = Double.MAX_VALUE
        val looks = arrayOf(
            floatArrayOf(player.yRot, player.xRot),
            floatArrayOf(player.yRotO, player.xRot),
            floatArrayOf(player.yRotO, player.xRotO)
        )
        for ((yaw, pitch) in looks) {
            val end = eye.add(RotationUtil.getVectorForRotation(yaw, pitch).scale(traceLength))
            val hit = intercept(box, eye, end) ?: continue
            minimum = min(minimum, eye.distanceTo(hit))
        }
        return minimum
    }

    fun canHitWithoutFlag(player: LivingEntity?, rawBox: AABB?, configuredRange: Double): Boolean {
        if (player == null || rawBox == null || isKnownInvalid(player, rawBox, configuredRange)) return false
        val traced = tracedDistance(player, rawBox, configuredRange)
        return traced != Double.MAX_VALUE && traced <= grimMaxReach(player, configuredRange)
    }

    fun scorePosition(player: LivingEntity, currentBox: AABB, serverBox: AABB, configuredRange: Double): Double {
        if (!canHitWithoutFlag(player, currentBox, configuredRange)) return Double.NEGATIVE_INFINITY
        val current = minDistanceToBox(player.getEyePosition(), expandForGrim(currentBox, player))
        val server = minDistanceToBox(player.getEyePosition(), expandForGrim(serverBox, player))
        val exposure = server - current
        return (grimMaxReach(player, configuredRange) - current) + exposure * 1.35
    }

    fun boxFromPosition(entity: Entity, position: Vec3): AABB {
        val current = entity.boundingBox
        val halfWidth = current.getXsize() / 2.0
        val height = current.getYsize()
        return AABB(
            position.x - halfWidth,
            position.y,
            position.z - halfWidth,
            position.x + halfWidth,
            position.y + height,
            position.z + halfWidth
        )
    }

    fun union(first: AABB?, second: AABB?): AABB? = when {
        first == null -> second
        second == null -> first
        else -> first.minmax(second)
    }

    fun isInsideCompensatedUnion(player: LivingEntity?, visual: AABB?, server: AABB?, configuredRange: Double): Boolean {
        if (player == null || visual == null) return false
        val union = union(visual, server ?: visual) ?: return false
        val compensated = expandForGrim(union, player)
        return minDistanceToBox(player.getEyePosition(), compensated) <= grimMaxReach(player, configuredRange) &&
            tracedDistance(player, union, configuredRange) != Double.MAX_VALUE
    }
}
