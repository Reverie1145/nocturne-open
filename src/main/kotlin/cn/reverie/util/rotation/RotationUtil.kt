package cn.reverie.util.rotation

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object RotationUtil {

    const val RAD_TO_DEG: Double = 180.0 / Math.PI

    fun nearestRotation(box: AABB): FloatArray? {
        val mc = Minecraft.getInstance()
        if (mc.player == null) return null
        val targetPoint = getNearestPointBB(box)
        return getRotations(targetPoint)
    }

    fun getNearestPointBB(box: AABB): Vec3 {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return Vec3(box.minX, box.minY, box.minZ)
        val eyePos = player.eyePosition

        val x = eyePos.x.coerceIn(box.minX, box.maxX)
        val y = eyePos.y.coerceIn(box.minY, box.maxY)
        val z = eyePos.z.coerceIn(box.minZ, box.maxZ)
        val nearestPoint = Vec3(x, y, z)

        if (isVisible(nearestPoint)) {
            return nearestPoint
        }

        val scanBox = box.inflate(-0.002)
        val stepX = scanBox.xsize
        val stepY = scanBox.ysize
        val stepZ = scanBox.zsize

        var bestPoint: Vec3? = null
        var minDistanceSq = Double.MAX_VALUE

        var sX = scanBox.minX
        while (sX <= scanBox.maxX) {
            var sY = scanBox.minY
            while (sY <= scanBox.maxY) {
                var sZ = scanBox.minZ
                while (sZ <= scanBox.maxZ) {
                    val currentPoint = Vec3(sX, sY, sZ)
                    if (isVisible(currentPoint)) {
                        val distSq = eyePos.distanceToSqr(currentPoint)
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq
                            bestPoint = currentPoint
                        }
                    }
                    sZ += max(stepZ / 2.0, 0.1)
                }
                sY += max(stepY / 2.0, 0.1)
            }
            sX += max(stepX / 2.0, 0.1)
        }

        return bestPoint ?: nearestPoint
    }

    fun isVisible(targetVec: Vec3): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val level = mc.level ?: return false

        val eyePos = player.eyePosition
        if (eyePos.distanceToSqr(targetVec) > 4096.0) return false
        val lookVec = getVectorForRotation(player.yRot, player.xRot)
        val toTarget = targetVec.subtract(eyePos).normalize()
        if (lookVec.dot(toTarget) < 0.0) return false

        val context = ClipContext(eyePos, targetVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)
        return level.clip(context).type == HitResult.Type.MISS
    }

    fun getDistanceToEntity(target: LivingEntity?): Float {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return 0.0f
        if (target == null) return 0.0f
        val eyePos = player.eyePosition
        val nearestPoint = getNearestPointBB(target.boundingBox)
        return eyePos.distanceTo(nearestPoint).toFloat()
    }

    fun getRotationDifference(entity: LivingEntity?): Double {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return 0.0
        if (entity == null) return 0.0

        val targetPos = entity.position()
        val neededRotations = getRotations(targetPos) ?: return 180.0

        val yawDiff = abs(Mth.wrapDegrees(player.yRot - neededRotations[0]))
        val pitchDiff = abs(Mth.wrapDegrees(player.xRot - neededRotations[1]))

        return sqrt((yawDiff * yawDiff + pitchDiff * pitchDiff).toDouble())
    }

    fun getRotations(pos: BlockPos): FloatArray? {
        val player = Minecraft.getInstance().player ?: return null
        return getRotations(
            pos.x + 0.5, pos.y + 0.5, pos.z + 0.5,
            player.x, player.y + player.getEyeHeight(player.pose), player.z
        )
    }

    fun getRotations(pos: BlockPos, facing: Direction): FloatArray? {
        val player = Minecraft.getInstance().player ?: return null
        val targetX = pos.x + 0.5 + facing.stepX * 0.5
        val targetY = pos.y + 0.5 + facing.stepY * 0.5
        val targetZ = pos.z + 0.5 + facing.stepZ * 0.5
        return getRotations(Vec3(targetX, targetY, targetZ))
    }

    fun getRotations(target: Vec3): FloatArray? {
        val player = Minecraft.getInstance().player ?: return null
        return getRotations(player.eyePosition, target)
    }

    fun getRotations(origin: Vec3, target: Vec3): FloatArray {
        return getRotations(target.x, target.y, target.z, origin.x, origin.y, origin.z)
    }

    fun getRotations(
        rotX: Double, rotY: Double, rotZ: Double,
        startX: Double, startY: Double, startZ: Double
    ): FloatArray {
        val diffX = rotX - startX
        val diffY = rotY - startY
        val diffZ = rotZ - startZ
        val dist = sqrt(diffX * diffX + diffZ * diffZ)
        val yaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()
        val pitch = (-Math.toDegrees(atan2(diffY, dist))).toFloat()
        return floatArrayOf(yaw, pitch)
    }

    fun getNearestRotation(
        pos: BlockPos, facing: Direction,
        currentRotation: FloatArray, shrink: Double
    ): FloatArray {
        val player = Minecraft.getInstance().player ?: return currentRotation
        val eyePos = player.eyePosition
        val lookVec = getVectorForRotation(currentRotation[0], currentRotation[1])

        var minX = pos.x.toDouble(); var minY = pos.y.toDouble(); var minZ = pos.z.toDouble()
        var maxX = minX + 1.0; var maxY = minY + 1.0; var maxZ = minZ + 1.0

        when (facing) {
            Direction.UP -> minY = maxY
            Direction.DOWN -> maxY = minY
            Direction.NORTH -> maxZ = minZ
            Direction.SOUTH -> minZ = maxZ
            Direction.WEST -> maxX = minX
            Direction.EAST -> minX = maxX
        }

        val axis = facing.axis
        if (axis != Direction.Axis.X) { minX += shrink; maxX -= shrink }
        if (axis != Direction.Axis.Y) { minY += shrink; maxY -= shrink }
        if (axis != Direction.Axis.Z) { minZ += shrink; maxZ -= shrink }

        val nearest = closestPointOnPlaneToRay(eyePos, lookVec, facing, minX, minY, minZ, maxX, maxY, maxZ)
        return getRotations(nearest) ?: currentRotation
    }

    private fun closestPointOnPlaneToRay(
        eye: Vec3, dir: Vec3, facing: Direction,
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double
    ): Vec3 {
        val axis = facing.axis
        val planeCoord = when (axis) {
            Direction.Axis.X -> minX
            Direction.Axis.Y -> minY
            Direction.Axis.Z -> minZ
        }
        val eyeC = when (axis) {
            Direction.Axis.X -> eye.x
            Direction.Axis.Y -> eye.y
            Direction.Axis.Z -> eye.z
        }
        val dirC = when (axis) {
            Direction.Axis.X -> dir.x
            Direction.Axis.Y -> dir.y
            Direction.Axis.Z -> dir.z
        }

        if (abs(dirC) > 1e-7) {
            val t = (planeCoord - eyeC) / dirC
            if (t > 0) {
                val hit = eye.add(dir.scale(t))
                return Vec3(
                    hit.x.coerceIn(minX, maxX),
                    hit.y.coerceIn(minY, maxY),
                    hit.z.coerceIn(minZ, maxZ)
                )
            }
        }

        var best: Vec3? = null
        var bestDot = -Double.MAX_VALUE
        for (x in doubleArrayOf(minX, maxX)) {
            for (y in doubleArrayOf(minY, maxY)) {
                for (z in doubleArrayOf(minZ, maxZ)) {
                    val p = Vec3(x, y, z)
                    val dot = dir.dot(p.subtract(eye).normalize())
                    if (dot > bestDot) {
                        bestDot = dot
                        best = p
                    }
                }
            }
        }
        return best ?: Vec3(minX, minY, minZ)
    }

    fun getSmoothRotation(lastRotation: FloatArray, targetRotation: FloatArray, speed: Double): FloatArray {
        val lastYaw = lastRotation[0]
        val lastPitch = lastRotation[1]
        var yaw = lastYaw
        var pitch = lastPitch

        if (speed != 0.0) {
            val deltaYaw = Mth.wrapDegrees(targetRotation[0] - lastYaw).toDouble()
            val deltaPitch = (Mth.clamp(targetRotation[1], -90.0f, 90.0f) - lastPitch).toDouble()
            val distance = sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch)

            if (distance > 1.0E-4) {
                val distributionYaw = abs(deltaYaw / distance)
                val distributionPitch = abs(deltaPitch / distance)
                val maxYaw = speed * distributionYaw
                val maxPitch = speed * distributionPitch
                yaw = lastYaw + deltaYaw.coerceIn(-maxYaw, maxYaw).toFloat()
                pitch = lastPitch + deltaPitch.coerceIn(-maxPitch, maxPitch).toFloat()
            }
        } else {
            yaw = targetRotation[0]
            pitch = targetRotation[1]
        }

        pitch = Mth.clamp(pitch, -90.0f, 90.0f)
        var patched = applySensitivityPatch(floatArrayOf(yaw, pitch), lastRotation)

        if (patched[0] == lastYaw && patched[1] == lastPitch) {
            patched = nudgeRotation(lastYaw, lastPitch, if (Math.random() < 0.5) 1 else -1)
        }

        patched[1] = Mth.clamp(patched[1], -90.0f, 90.0f)
        return patched
    }

    fun nudgeRotation(yaw: Float, pitch: Float, sign: Int): FloatArray {
        val direction = if (sign >= 0) 1 else -1
        val previous = floatArrayOf(yaw, pitch)
        for (ticks in 1..4) {
            val patched = applySensitivityPatch(floatArrayOf(yaw + direction * ticks, pitch), previous)
            if (patched[0] != yaw || patched[1] != pitch) {
                patched[1] = Mth.clamp(patched[1], -90.0f, 90.0f)
                return patched
            }
        }
        return floatArrayOf(yaw + direction * getGcd().toFloat(), Mth.clamp(pitch, -90.0f, 90.0f))
    }

    fun getGcd(): Double {
        val mc = Minecraft.getInstance()
        val sensitivity = (mc.options.sensitivity().get() * 0.6f + 0.2f).toDouble()
        return sensitivity * sensitivity * sensitivity * 8.0 * 0.15
    }

    fun applySensitivityPatch(rotation: FloatArray, previousRotation: FloatArray): FloatArray {
        val multiplier = getGcd()
        val yaw = previousRotation[0] + (Math.round((rotation[0] - previousRotation[0]) / multiplier) * multiplier).toFloat()
        val pitch = previousRotation[1] + (Math.round((rotation[1] - previousRotation[1]) / multiplier) * multiplier).toFloat()
        return floatArrayOf(yaw, Mth.clamp(pitch, -90.0f, 90.0f))
    }

    fun getVectorForRotation(yaw: Float, pitch: Float): Vec3 {
        val yawRad = yaw * (Math.PI.toFloat() / 180f)
        val pitchRad = pitch * (Math.PI.toFloat() / 180f)
        val cosYaw = Mth.cos((-yawRad - Math.PI.toFloat()).toDouble())
        val sinYaw = Mth.sin((-yawRad - Math.PI.toFloat()).toDouble())
        val cosPitch = -Mth.cos((-pitchRad).toDouble())
        val sinPitch = Mth.sin((-pitchRad).toDouble())
        return Vec3((sinYaw * cosPitch).toDouble(), sinPitch.toDouble(), (cosYaw * cosPitch).toDouble())
    }

    fun toRotation(target: Vec3, predict: Boolean = false): Rotation {
        val rots = getRotations(target)
        return if (rots != null) Rotation(rots[0], rots[1]) else Rotation.ZERO
    }

    fun vecToRotation(target: Vec3, predict: Boolean = false): Rotation {
        val player = Minecraft.getInstance().player ?: return Rotation(0.0f, 0.0f)
        var eyesPos = player.eyePosition
        if (predict) eyesPos = eyesPos.add(player.deltaMovement)

        val diffX = target.x - eyesPos.x
        val diffY = target.y - eyesPos.y
        val diffZ = target.z - eyesPos.z
        val distXZ = sqrt(diffX * diffX + diffZ * diffZ)
        return Rotation(
            (atan2(diffZ, diffX) * RAD_TO_DEG - 90.0).toFloat(),
            (-atan2(diffY, distXZ) * RAD_TO_DEG).toFloat()
        )
    }

    fun getRotationDifference(first: Rotation, second: Rotation): Double {
        return hypot(getAngleDifference(first.yaw, second.yaw).toDouble(), (first.pitch - second.pitch).toDouble())
    }

    fun getAngleDifference(first: Float, second: Float): Float {
        return (((first - second) % 360.0f + 540.0f) % 360.0f) - 180.0f
    }

    fun getRotationOrElseMC(): Rotation {
        val player = Minecraft.getInstance().player ?: return Rotation(0.0f, 0.0f)
        val rotation = RotationManager.targetRotation
        return rotation ?: Rotation(player.yRot, player.xRot)
    }
}
