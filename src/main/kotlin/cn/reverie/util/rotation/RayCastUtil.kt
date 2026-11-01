package cn.reverie.util.rotation

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object RayCastUtil {

    fun rayCast(
        rotation: Rotation,
        range: Double,
        expand: Float = 0f,
        entity: Entity? = Minecraft.getInstance().player,
        boxOverride: ((Entity) -> AABB?)? = null,
        entityAllowed: ((Entity) -> Boolean)? = null
    ): HitResult? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val source = entity ?: return null

        val eyePos = source.eyePosition
        val lookVec = RotationUtil.getVectorForRotation(rotation.yaw, rotation.pitch)
        val endVec = eyePos.add(lookVec.scale(range))

        val objectMouseOver = level.clip(
            ClipContext(
                eyePos,
                endVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                source
            )
        )

        var distToBlock = range
        if (objectMouseOver.type != HitResult.Type.MISS) {
            distToBlock = objectMouseOver.location.distanceTo(eyePos)
        }

        val entitySearchEndVec = eyePos.add(lookVec.scale(range))
        var pointedEntity: Entity? = null
        var hitVec: Vec3? = null

        val searchBox = source.boundingBox.expandTowards(lookVec.scale(range)).inflate(1.0)
        val entities: Iterable<Entity> = if (boxOverride == null) {
            level.getEntities(source, searchBox) { e -> e.isAlive && !e.isSpectator && e.isPickable }
        } else {
            // The level's spatial index still uses the frozen box while a held
            // movement packet supplies a newer server-position estimate.
            level.entitiesForRendering().filter { e ->
                e !== source && e.isAlive && !e.isSpectator && e.isPickable
            }
        }

        var closestEntityDist = distToBlock

        for (possibleEntity in entities) {
            if (entityAllowed?.invoke(possibleEntity) == false) continue
            var entityBox: AABB = (boxOverride?.invoke(possibleEntity) ?: possibleEntity.boundingBox)
                .inflate(possibleEntity.pickRadius.toDouble())
            if (expand != 0.0f) {
                entityBox = entityBox.inflate(expand.toDouble())
            }

            val intercept = entityBox.clip(eyePos, entitySearchEndVec)

            if (entityBox.contains(eyePos)) {
                if (closestEntityDist >= 0.0) {
                    pointedEntity = possibleEntity
                    hitVec = intercept.orElse(eyePos)
                    closestEntityDist = 0.0
                }
            } else if (intercept.isPresent) {
                val distToEntity = eyePos.distanceTo(intercept.get())

                if (distToEntity < closestEntityDist || closestEntityDist == 0.0) {
                    pointedEntity = possibleEntity
                    hitVec = intercept.get()
                    closestEntityDist = distToEntity
                }
            }
        }

        if (pointedEntity != null && (closestEntityDist < distToBlock || objectMouseOver.type == HitResult.Type.MISS)) {
            return EntityHitResult(pointedEntity, hitVec ?: eyePos)
        }

        return objectMouseOver
    }

    fun inView(entity: Entity?): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (entity == null) return false

        val renderDistance = mc.options.renderDistance().get() * 16.0
        val targetRot = RotationUtil.getRotations(entity.position()) ?: return false
        val rotations = Rotation(targetRot[0], targetRot[1])

        if (abs(Mth.wrapDegrees(player.yRot - rotations.yaw)) > mc.options.fov().get()) {
            return false
        }

        val dist = player.distanceTo(entity)

        if (dist > 100 || entity !is Player) {
            val result = rayCast(rotations, renderDistance, 0.2f, player)
            return result != null && result.type == HitResult.Type.ENTITY
        } else {
            val bb = entity.boundingBox
            val width = bb.xsize
            val height = bb.ysize
            val depth = bb.zsize
            val basePos = entity.position()

            var yPercent = 1.0
            while (yPercent >= -1.0) {
                var xPercent = 1.0
                while (xPercent >= -1.0) {
                    var zPercent = 1.0
                    while (zPercent >= -1.0) {
                        val targetPoint = basePos.add(width * xPercent, height * yPercent, depth * zPercent)
                        val subRot = RotationUtil.getRotations(targetPoint)
                        if (subRot != null) {
                            val subRotations = Rotation(subRot[0], subRot[1])
                            val result = rayCast(subRotations, renderDistance, 0.2f, player)
                            if (result != null && result.type == HitResult.Type.ENTITY) {
                                return true
                            }
                        }
                        zPercent -= 1.0
                    }
                    xPercent -= 1.0
                }
                yPercent -= 0.5
            }
        }

        return false
    }

    fun didHitBlockFace(player: Player?, yaw: Float, pitch: Float, targetPos: BlockPos, expectedFace: Direction, strict: Boolean): Boolean {
        if (player == null) {
            return false
        }

        val result = rayCastBlock(
            Rotation(yaw, pitch),
            player,
            player.level(),
            player.blockInteractionRange()
        )
        return result != null
                && result.type == HitResult.Type.BLOCK
                && result.blockPos == targetPos
                && (!strict || result.direction == expectedFace)
    }

    fun overBlock(targetPos: BlockPos, expectedFace: Direction, strict: Boolean = false): Boolean {
        val mc = Minecraft.getInstance()
        val hit = mc.hitResult
        if (hit is BlockHitResult && hit.blockPos == targetPos) {
            return !strict || hit.direction == expectedFace
        }
        val rotation = RotationManager.currentRotation ?: RotationManager.targetRotation
        return rotation != null && didHitBlockFace(
            mc.player,
            rotation.yaw,
            rotation.pitch,
            targetPos,
            expectedFace,
            strict
        )
    }

    fun hitBlock(yaw: Float, pitch: Float, reach: Double): BlockHitResult? {
        val player = Minecraft.getInstance().player ?: return null
        val level = Minecraft.getInstance().level ?: return null
        return rayCastBlock(Rotation(yaw, pitch), player, level, reach)
    }

    fun rayCastBlock(reach: Double, predictTicks: Double = 0.0): BlockPos? {
        return rayCastBlock(RotationUtil.getRotationOrElseMC(), reach, predictTicks)
    }

    fun rayCastBlock(rotation: Rotation?, reach: Double, predictTicks: Double = 0.0): BlockPos? {
        val rot = rotation ?: RotationUtil.getRotationOrElseMC()
        val player = Minecraft.getInstance().player ?: return null
        val level = Minecraft.getInstance().level ?: return null

        val blockHit = rayCastBlock(rot, player, level, reach, predictTicks)
        return if (blockHit != null && blockHit.type == HitResult.Type.BLOCK) blockHit.blockPos else null
    }

    fun rayCastBlock(rotation: Rotation?, entity: Entity?, level: Level?, reach: Double, predictTicks: Double = 0.0): BlockHitResult? {
        if (rotation == null || entity == null || level == null || reach < 0.0) {
            return null
        }

        var start = entity.eyePosition
        if (predictTicks != 0.0) {
            val motion = entity.deltaMovement
            start = start.add(motion.x * predictTicks, motion.y * predictTicks, motion.z * predictTicks)
        }
        val direction = RotationUtil.getVectorForRotation(rotation.yaw, rotation.pitch)
        val end = start.add(direction.scale(reach))

        return level.clip(
            ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                entity
            )
        )
    }
}

typealias RayCastUtils = RayCastUtil
