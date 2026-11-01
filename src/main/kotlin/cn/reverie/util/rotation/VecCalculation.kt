package cn.reverie.util.rotation

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object VecCalculation {

    fun getBestAimVec(player: Entity, target: Entity, prediction: Float, alwaysHavePoint: Boolean): Vec3? {
        var eyePos = player.eyePosition
        var targetBox = target.boundingBox

        if (prediction != 0f) {
            eyePos = eyePos.add(player.deltaMovement.scale(prediction.toDouble()))
            targetBox = targetBox.move(target.deltaMovement.scale(prediction.toDouble()))
        }

        val possiblePoints = ArrayList<Vec3>()
        val targetCenter = targetBox.center

        var i = 0.2
        while (i <= 0.9) {
            possiblePoints.add(Vec3(targetCenter.x, targetBox.minY + target.bbHeight * i, targetCenter.z))
            i += 0.2
        }

        possiblePoints.add(Vec3(targetBox.minX, targetCenter.y, targetCenter.z))
        possiblePoints.add(Vec3(targetBox.maxX, targetCenter.y, targetCenter.z))
        possiblePoints.add(Vec3(targetCenter.x, targetBox.minY, targetCenter.z))
        possiblePoints.add(Vec3(targetCenter.x, targetBox.maxY, targetCenter.z))
        possiblePoints.add(Vec3(targetCenter.x, targetCenter.y, targetBox.minZ))
        possiblePoints.add(Vec3(targetCenter.x, targetCenter.y, targetBox.maxZ))

        var bestPoint: Vec3? = null
        var bestScore = Double.MAX_VALUE

        for (point in possiblePoints) {
            if (!isNotBlocked(player.level(), eyePos, point, player))
                continue

            val distToEye = eyePos.distanceToSqr(point)
            val distToCenter = point.distanceToSqr(targetCenter)

            val eyeWeight = 0.7
            val centerWeight = 0.3
            val score = distToEye * eyeWeight + distToCenter * centerWeight

            if (score < bestScore) {
                bestScore = score
                bestPoint = point
            }
        }

        if (bestPoint == null && alwaysHavePoint) {
            val x = eyePos.x.coerceIn(targetBox.minX, targetBox.maxX)
            val y = eyePos.y.coerceIn(targetBox.minY, targetBox.maxY)
            val z = eyePos.z.coerceIn(targetBox.minZ, targetBox.maxZ)
            bestPoint = Vec3(x, y, z)
        }

        return bestPoint
    }

    fun getHitBox(entity: Entity): AABB {
        val borderSize = entity.pickRadius.toDouble()
        return entity.boundingBox.inflate(borderSize)
    }

    fun newGetPointToEntityBoxSafe(from: Entity, to: Entity, throughwallRange: Double): Vec3? {
        return newGetPointToEntityBoxSafe(from, to, throughwallRange, to.boundingBox)
    }

    fun newGetPointToEntityBoxSafe(from: Entity, to: Entity, throughwallRange: Double, box: AABB): Vec3? {
        val eye = from.eyePosition
        val level = from.level()
        val targetBox = to.boundingBox
        val targetEye = to.eyePosition.add(
            box.minX - targetBox.minX,
            box.minY - targetBox.minY,
            box.minZ - targetBox.minZ
        )

        val nearest = getNearestPointBB(eye, box)
        val d = eye.distanceTo(nearest)
        if (d <= throughwallRange) return nearest

        if (isNotBlocked(level, eye, nearest, from)) {
            return nearest
        }

        var best = Double.MAX_VALUE
        var bestPoint: Vec3? = null

        val center = box.center
        val samples = arrayOf(
            center,
            Vec3(center.x, box.maxY - 0.1, center.z),
            targetEye,
            Vec3(center.x, (box.minY + box.maxY) * 0.5, center.z),
            Vec3(center.x, box.minY + 0.2, center.z)
        )

        for (p in samples) {
            val dist = eye.distanceTo(p)
            if (dist >= best) continue

            if (dist <= throughwallRange || isNotBlocked(level, eye, p, from)) {
                best = dist
                bestPoint = p
            }
        }

        return bestPoint
    }

    fun newGetDistanceToEntityBoxSafe(from: Entity, to: Entity, throughwallRange: Double): Double {
        return newGetDistanceToEntityBoxSafe(from, to, throughwallRange, to.boundingBox)
    }

    fun newGetDistanceToEntityBoxSafe(from: Entity, to: Entity, throughwallRange: Double, box: AABB): Double {
        val eye = from.eyePosition
        val level = from.level()
        val targetBox = to.boundingBox
        val targetEye = to.eyePosition.add(
            box.minX - targetBox.minX,
            box.minY - targetBox.minY,
            box.minZ - targetBox.minZ
        )

        val nearest = getNearestPointBB(eye, box)
        val d = eye.distanceTo(nearest)
        if (d <= throughwallRange) return d

        var best = Double.MAX_VALUE
        if (isNotBlocked(level, eye, nearest, from)) {
            best = d
        }

        val center = box.center
        val samples = arrayOf(
            center,
            Vec3(center.x, box.maxY - 0.1, center.z),
            targetEye,
            Vec3(center.x, (box.minY + box.maxY) * 0.5, center.z),
            Vec3(center.x, box.minY + 0.2, center.z)
        )

        for (p in samples) {
            val dist = eye.distanceTo(p)
            if (dist >= best) continue

            if (dist <= throughwallRange || isNotBlocked(level, eye, p, from)) {
                best = dist
            }
        }

        return best
    }

    fun getNearestPointBB(eye: Vec3, box: AABB): Vec3 {
        val x = eye.x.coerceIn(box.minX, box.maxX)
        val y = eye.y.coerceIn(box.minY, box.maxY)
        val z = eye.z.coerceIn(box.minZ, box.maxZ)
        return Vec3(x, y, z)
    }

    fun getDistanceToEntityBox(from: Entity, to: Entity): Double {
        val eyes = from.eyePosition
        val nearestPoint = getNearestPointBB(eyes, getHitBox(to))
        return eyes.distanceTo(nearestPoint)
    }

    fun isNotBlocked(level: Level, start: Vec3, end: Vec3, entity: Entity): Boolean {
        val context = ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)
        val hitResult = level.clip(context)
        return hitResult.type == HitResult.Type.MISS
    }
}
