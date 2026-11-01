package cn.reverie.module.combat.projectileaura

import cn.reverie.module.combat.ProjectileAura

import cn.reverie.util.rotation.*
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
internal object ProjectileTrajectorySolver {
    fun solve(player: LocalPlayer, target: LivingEntity, item: Item, predictionFactor: Float, useRayCast: Boolean): ProjectileAura.TrajectoryResult? {
        val level = player.level()
        val eyePos = Vec3(player.x, player.eyeY - 0.1, player.z)

        val (speed, gravity, drag) = when {
            item == Items.SNOWBALL || item == Items.EGG || item == Items.ENDER_PEARL -> Triple(1.5, 0.03, 0.99)
            item == Items.SPLASH_POTION || item == Items.LINGERING_POTION -> Triple(0.5, 0.05, 0.99)
            item == Items.FISHING_ROD -> Triple(1.5, 0.04, 0.92)
            item.descriptionId.contains("wind_charge") -> Triple(1.5, 0.0, 1.0)
            else -> Triple(1.5, 0.03, 0.99)
        }

        val targetVelocity = target.deltaMovement
        val dist = eyePos.distanceTo(target.position())
        val approxTicks = (dist / speed).toInt().coerceIn(1, 40)
        val minTicks = (approxTicks - 4).coerceAtLeast(1)
        val maxTicks = (approxTicks + 8).coerceAtMost(40)

        val pFactor = predictionFactor

        var bestResult: ProjectileAura.TrajectoryResult? = null
        var bestDifference = Double.MAX_VALUE

        for (t in minTicks..maxTicks) {
            val predX = target.x + targetVelocity.x * t * pFactor
            val predZ = target.z + targetVelocity.z * t * pFactor
            val predY = if (target.onGround() && targetVelocity.y <= 0.0) {
                target.y
            } else {
                val yDrop = 0.5 * 0.08 * t * t
                (target.y + (targetVelocity.y * t - yDrop) * pFactor).coerceAtLeast(target.y - 4.0)
            }

            val predCenter = Vec3(predX, predY + target.bbHeight * 0.5, predZ)
            val halfWidth = target.bbWidth * 0.5
            val predBox = AABB(
                predX - halfWidth,
                predY,
                predZ - halfWidth,
                predX + halfWidth,
                predY + target.bbHeight,
                predZ + halfWidth
            )

            val diffX = predCenter.x - eyePos.x
            val diffZ = predCenter.z - eyePos.z
            val yaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()

            var lowPitch = -89.0f
            var highPitch = 89.0f
            var hitFound = false
            var hitPitch = 0.0f

            for (iter in 0..12) {
                val midPitch = (lowPitch + highPitch) * 0.5f

                var simPos = eyePos
                var motion = RotationUtil.getVectorForRotation(yaw, midPitch).scale(speed)
                var reached = false
                var blocked = false

                for (step in 1..t) {
                    val nextPos = simPos.add(motion)

                    if (useRayCast) {
                        val clip = level.clip(ClipContext(simPos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                        if (clip.type != HitResult.Type.MISS) {
                            blocked = true
                            break
                        }
                    }

                    if (step == t || predBox.intersects(AABB(simPos, nextPos).inflate(0.15))) {
                        reached = true
                        val yDiff = nextPos.y - predCenter.y
                        if (predBox.inflate(0.1).contains(nextPos) || abs(yDiff) < target.bbHeight * 0.6) {
                            hitFound = true
                            hitPitch = midPitch
                        } else if (nextPos.y > predCenter.y) {
                            lowPitch = midPitch
                        } else {
                            highPitch = midPitch
                        }
                        break
                    }

                    simPos = nextPos
                    motion = motion.scale(drag).subtract(0.0, gravity, 0.0)
                }

                if (blocked) {
                    break
                }
                if (hitFound) {
                    break
                }
                if (!reached) {
                    if (midPitch > -15.0f) {
                        highPitch = midPitch
                    } else {
                        lowPitch = midPitch
                    }
                }
            }

            if (hitFound) {
                val candidateRot = Rotation(yaw, hitPitch)
                val currentRot = RotationManager.currentRotation ?: Rotation(player.yRot, player.xRot)
                val rotDiff = RotationUtil.getRotationDifference(currentRot, candidateRot)
                if (rotDiff < bestDifference) {
                    bestDifference = rotDiff
                    bestResult = ProjectileAura.TrajectoryResult(candidateRot, t, predCenter)
                }
            }
        }

        if (bestResult == null) {
            val t = approxTicks
            val predX = target.x + targetVelocity.x * t * pFactor
            val predZ = target.z + targetVelocity.z * t * pFactor
            val predY = if (target.onGround() && targetVelocity.y <= 0.0) target.y else target.y + targetVelocity.y * t * pFactor
            val predCenter = Vec3(predX, predY + target.bbHeight * 0.5, predZ)

            val diffX = predCenter.x - eyePos.x
            val diffY = predCenter.y - eyePos.y
            val diffZ = predCenter.z - eyePos.z
            val distXZ = sqrt(diffX * diffX + diffZ * diffZ)

            val yaw = (Math.toDegrees(atan2(diffZ, diffX)) - 90.0).toFloat()
            val drop = 0.5 * gravity * t * t
            val compensatedY = diffY + drop
            val pitch = (-Math.toDegrees(atan2(compensatedY, distXZ))).toFloat().coerceIn(-89.0f, 89.0f)

            if (!useRayCast || VecCalculation.isNotBlocked(level, eyePos, predCenter, player)) {
                bestResult = ProjectileAura.TrajectoryResult(Rotation(yaw, pitch), t, predCenter)
            }
        }

        return bestResult
    }

}
