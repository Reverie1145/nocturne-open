package cn.reverie.util.rotation

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.hypot

object RotationSolver {

    fun getRotations(origin: Vec3, target: Vec3): FloatArray = RotationUtil.getRotations(origin, target)

    fun getRotations(
        rotX: Double, rotY: Double, rotZ: Double,
        startX: Double, startY: Double, startZ: Double
    ): FloatArray = RotationUtil.getRotations(rotX, rotY, rotZ, startX, startY, startZ)

    fun getVectorForRotation(yaw: Float, pitch: Float): Vec3 = RotationUtil.getVectorForRotation(yaw, pitch)

    fun getGcd(): Double = RotationUtil.getGcd()

    fun applySensitivityPatch(rotation: FloatArray, previousRotation: FloatArray): FloatArray =
        RotationUtil.applySensitivityPatch(rotation, previousRotation)

    fun nudgeRotation(yaw: Float, pitch: Float, sign: Int): FloatArray =
        RotationUtil.nudgeRotation(yaw, pitch, sign)

    fun getSmoothRotation(lastRotation: FloatArray, targetRotation: FloatArray, speed: Double): FloatArray =
        RotationUtil.getSmoothRotation(lastRotation, targetRotation, speed)

    fun getNearestRotation(
        pos: BlockPos, facing: Direction,
        currentRotation: FloatArray, shrink: Double
    ): FloatArray = RotationUtil.getNearestRotation(pos, facing, currentRotation, shrink)

    fun movementInputToVelocity(movementInput: Vec3, speed: Float, yaw: Float): Vec3 {
        val length = movementInput.lengthSqr()
        if (length < 1.0E-7) {
            return Vec3.ZERO
        }
        val movement = (if (length > 1.0) movementInput.normalize() else movementInput).scale(speed.toDouble())
        val sin = Mth.sin((yaw * (Math.PI.toFloat() / 180f)).toDouble())
        val cos = Mth.cos((yaw * (Math.PI.toFloat() / 180f)).toDouble())
        return Vec3(
            movement.x * cos - movement.z * sin,
            movement.y,
            movement.z * cos + movement.x * sin
        )
    }

    fun getDirectionDeg(rotationYaw: Float, moveForward: Float, moveStrafe: Float): Double {
        var yaw = rotationYaw
        if (moveForward < 0f) yaw += 180f
        val forward = if (moveForward < 0f) -0.5f else if (moveForward > 0f) 0.5f else 1f
        if (moveStrafe > 0f) yaw -= 90f * forward
        if (moveStrafe < 0f) yaw += 90f * forward
        return yaw.toDouble()
    }

    fun aimPoint(attacker: LivingEntity, target: Entity): Vec3 {
        val box = target.boundingBox
        val center = box.center
        val eye = attacker.eyePosition
        if (box.contains(eye)) {
            return eye
        }
        return Vec3(center.x, Mth.clamp(eye.y, box.minY + 0.1, box.maxY - 0.1), center.z)
    }

    fun reachTo(attacker: LivingEntity, target: Entity): Double {
        val eye = attacker.eyePosition
        val hit = Vec3(
            eye.x.coerceIn(target.boundingBox.minX, target.boundingBox.maxX),
            eye.y.coerceIn(target.boundingBox.minY, target.boundingBox.maxY),
            eye.z.coerceIn(target.boundingBox.minZ, target.boundingBox.maxZ)
        )
        return eye.distanceTo(hit)
    }

    fun lookHits(attacker: LivingEntity, target: Entity, yaw: Float, pitch: Float, maxReach: Double): Boolean {
        val eye = attacker.eyePosition
        val look = Vec3.directionFromRotation(pitch, yaw)
        val end = eye.add(look.x * maxReach, look.y * maxReach, look.z * maxReach)
        val box = target.boundingBox
        return box.clip(eye, end).isPresent || box.contains(eye)
    }

    fun fovDelta(fromYaw: Float, fromPitch: Float, targetYaw: Float, targetPitch: Float): Float {
        val yaw = abs(Mth.wrapDegrees(targetYaw - fromYaw))
        val pitch = abs(Mth.wrapDegrees(targetPitch - fromPitch))
        return hypot(yaw.toDouble(), pitch.toDouble()).toFloat()
    }

    fun turn(current: FloatArray, target: FloatArray, mode: String, speed: Float): FloatArray {
        val yawDiff = Mth.wrapDegrees(target[0] - current[0])
        val pitchDiff = Mth.clamp(target[1], -90f, 90f) - current[1]
        val patched = applySensitivityPatch(floatArrayOf(current[0] + yawDiff, current[1] + pitchDiff), current)
        val gcdYaw = patched[0] - current[0]
        val gcdPitch = patched[1] - current[1]
        val next = when (mode.lowercase()) {
            "instant" -> floatArrayOf(current[0] + gcdYaw, current[1] + gcdPitch)
            "linear" -> floatArrayOf(
                current[0] + Mth.clamp(gcdYaw, -speed, speed),
                current[1] + Mth.clamp(gcdPitch, -speed * 0.6f, speed * 0.6f)
            )
            "smooth" -> {
                val factor = Mth.clamp(speed / 180f, 0.08f, 1f)
                floatArrayOf(current[0] + gcdYaw * factor, current[1] + gcdPitch * factor)
            }
            "adaptive" -> {
                val dist = hypot(gcdYaw.toDouble(), gcdPitch.toDouble()).toFloat()
                val factor = if (dist > 40f) 0.55f else if (dist > 12f) 0.28f else 0.14f
                val cap = speed.coerceAtLeast(4f)
                floatArrayOf(
                    current[0] + Mth.clamp(gcdYaw * factor, -cap, cap),
                    current[1] + Mth.clamp(gcdPitch * factor, -cap * 0.6f, cap * 0.6f)
                )
            }
            else -> {
                val factor = Mth.clamp(speed / 180f, 0.12f, 1f)
                floatArrayOf(current[0] + gcdYaw * factor, current[1] + gcdPitch * factor)
            }
        }
        next[1] = Mth.clamp(next[1], -90f, 90f)
        return next
    }
}
