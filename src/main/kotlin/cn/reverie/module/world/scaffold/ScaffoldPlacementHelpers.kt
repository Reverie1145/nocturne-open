package cn.reverie.module.world.scaffold

import cn.reverie.module.world.Scaffold
import cn.reverie.module.movement.Stuck
import cn.reverie.util.rotation.GrimRayCastUtil
import cn.reverie.util.rotation.RayCastUtil
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationUtil
import cn.reverie.util.player.PlayerUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.protocol.game.ServerboundSwingPacket
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ClipContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

internal fun Scaffold.tryPlace(
    player: LocalPlayer,
    lastX: Double = player.xOld,
    lastY: Double = player.yOld,
    lastZ: Double = player.zOld,
    upcomingYaw: Float = player.yRot,
    upcomingPitch: Float = player.xRot
): Boolean {
    if (!canPlace) return false
    val placeData = data ?: return false
    val aim = placementAim ?: return false
    val mc = Minecraft.getInstance()
    val gameMode = mc.gameMode ?: return false
    val level = mc.level ?: return false
    if (isInstantRotation() && !onTickReadyToPlace) return false
    if (!canPlaceAtTarget(player)) return false
    if (placementMode.isMode("Direct") && !delayTimer.hasTimeElapsed(nextPlaceDelay)) return false

    val mainHand = player.mainHandItem
    if (!isValidBlock(mainHand) && !isValidBlock(player.offhandItem)) return false
    val hand = if (isValidBlock(mainHand)) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
    val destination = placeData.blockPos.relative(placeData.facing)
    if (!level.getBlockState(destination).canBeReplaced()) return false
    val hitVec = if (mode.isMode("Normal") && !fast.value) {
        randomClickVec(placeData.blockPos, placeData.facing)
    } else {
        aim.hitVec
    }
    if (!hasVisiblePlacementFace(player, placeData, hitVec)) return false
    if (rotationMode.isMode("Heypixel") && player.eyePosition.distanceToSqr(hitVec) > 20.25) return false
    updateAutoStuck(player, hitVec)
    val checkRotation = if (fast.value) RotationUtil.getRotations(player.eyePosition, hitVec) else floatArrayOf(upcomingYaw, upcomingPitch)
    if (!passesRaycast(placeData.blockPos, placeData.facing, lastX, lastY, lastZ, checkRotation[0], checkRotation[1])) return false
    if (!passesNeverPlacementWindow(player)) return false

    val hit = BlockHitResult(hitVec, placeData.facing, placeData.blockPos, false)
    val result = useScaffoldItemOn(gameMode, player, hand, hit)
    if (!result.consumesAction()) return false

    onPlaced(destination)
    when (swingMode.value) {
        "Client" -> player.swing(hand)
        "Server" -> player.connection.send(ServerboundSwingPacket(hand))
    }
    return true
}

/** Reject stale support blocks and fallback aim points that do not actually expose the placement face. */
private fun hasVisiblePlacementFace(player: LocalPlayer, info: Scaffold.PlaceInfo, point: Vec3): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    val eye = player.eyePosition
    val toward = point.subtract(eye)
    val distance = toward.length()
    val reach = player.blockInteractionRange()
    if (distance < 1.0e-6 || distance > reach) return false
    val end = eye.add(toward.scale((distance + 0.05).coerceAtMost(reach) / distance))
    val hit = level.clip(ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
    return hit.type == HitResult.Type.BLOCK && hit.blockPos == info.blockPos && hit.direction == info.facing
}

private fun Scaffold.passesNeverPlacementWindow(player: LocalPlayer): Boolean {
    if ((!mode.isMode("Telly") && !mode.isMode("SameY")) || !isNeverRotationMode()) return true
    if (rotationMode.isMode("Heypixel")) return true
    return offGroundTicks > 2 || (player.onGround() && PlayerUtil.onGroundTicks < jumpDelayMin.getInt())
}

private fun Scaffold.updateAutoStuck(player: LocalPlayer, hitVec: Vec3) {
    if (neverStuckTicks < 0) neverStuckTicks++

    val distance = player.eyePosition.distanceTo(hitVec)
    val heightAbovePlace = player.y - placeY + 1.0
    val badPlacementDistance =
        (distance > 3.0 && heightAbovePlace < 0.15) ||
        (distance > 2.0 && heightAbovePlace < 0.1) ||
        (distance > 4.0 && heightAbovePlace < 0.25) ||
        distance > 4.2 || player.fallDistance > 2.0

    val level = Minecraft.getInstance().level
    val groundedNearby = level != null && listOf(2.0, 1.5, 1.0, 0.5).any { depth ->
        level.getBlockCollisions(player, player.boundingBox.move(0.0, -depth, 0.0)).iterator().hasNext()
    }
    val shouldActivate = autoStuck.value && badPlacementDistance && neverStuckTicks in 0..20 &&
        !groundedNearby && !player.onGround()

    if (shouldActivate) {
        autoStuckActive = true
        if (!Stuck.enabled) {
            previousStuckMode = Stuck.mode.value
            Stuck.mode.value = autoStuckMode.value
            Stuck.enabled = true
            ownsStuckModule = true
        }
    } else {
        autoStuckActive = false
        if (ownsStuckModule && Stuck.enabled) Stuck.enabled = false
        previousStuckMode?.let { Stuck.mode.value = it }
        previousStuckMode = null
        ownsStuckModule = false
        if (neverStuckTicks > 0) neverStuckTicks = -2
    }
    if (shouldActivate) neverStuckTicks++
}

private fun Scaffold.canPlaceAtTarget(player: LocalPlayer): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    val under = BlockPos(floor(player.x).toInt(), placeY, floor(player.z).toInt())
    return level.getBlockState(under).isAir || level.getBlockState(under).canBeReplaced()
}

private fun Scaffold.randomClickVec(pos: BlockPos, face: Direction): Vec3 {
    val level = Minecraft.getInstance().level ?: return Vec3.atCenterOf(pos)
    val shape = level.getBlockState(pos).getCollisionShape(level, pos)
    val local = if (shape.isEmpty) AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0) else shape.bounds()
    val minX = pos.x + local.minX
    val minY = pos.y + local.minY
    val minZ = pos.z + local.minZ
    val maxX = pos.x + local.maxX
    val maxY = pos.y + local.maxY
    val maxZ = pos.z + local.maxZ
    val first = Random.nextDouble(0.2, 0.8)
    val second = Random.nextDouble(0.2, 0.8)
    return when (face) {
        Direction.DOWN -> Vec3(Mth.lerp(first, minX, maxX), minY, Mth.lerp(second, minZ, maxZ))
        Direction.UP -> Vec3(Mth.lerp(first, minX, maxX), maxY, Mth.lerp(second, minZ, maxZ))
        Direction.NORTH -> Vec3(Mth.lerp(first, minX, maxX), Mth.lerp(second, minY, maxY), minZ)
        Direction.SOUTH -> Vec3(Mth.lerp(first, minX, maxX), Mth.lerp(second, minY, maxY), maxZ)
        Direction.WEST -> Vec3(minX, Mth.lerp(first, minY, maxY), Mth.lerp(second, minZ, maxZ))
        Direction.EAST -> Vec3(maxX, Mth.lerp(first, minY, maxY), Mth.lerp(second, minZ, maxZ))
    }
}

internal fun Scaffold.passesRaycast(
    pos: BlockPos,
    facing: Direction,
    lastX: Double = Minecraft.getInstance().player?.x ?: 0.0,
    lastY: Double = Minecraft.getInstance().player?.y ?: 0.0,
    lastZ: Double = Minecraft.getInstance().player?.z ?: 0.0,
    yaw: Float = Minecraft.getInstance().player?.yRot ?: 0f,
    pitch: Float = Minecraft.getInstance().player?.xRot ?: 0f
): Boolean {
    val player = Minecraft.getInstance().player ?: return false
    val level = Minecraft.getInstance().level ?: return false

    if (rayCast.isMode("Strict")) {
        val box = AABB(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(), pos.x + 1.0, pos.y + 1.0, pos.z + 1.0)
        if (!GrimRayCastUtil.checkPositionPlace(player, box, facing, lastX, lastY, lastZ)) return false
        if (!GrimRayCastUtil.checkPositionPlace(player, box, facing, player.x, player.y, player.z)) return false
    }

    // Grim performs both position and rotation validation inside canPlaceGrim itself.
    return when (rayCast.value) {
        "Tight", "Wide" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, if (rayCast.isMode("Tight")) 3.5 else 5.0)
            hit?.blockPos == pos && hit.direction == facing
        }
        "Aim Point" -> {
            val aim = placementAim
            val hit = aim?.let { RayCastUtil.rayCastBlock(Rotation(it.rotation[0], it.rotation[1]), player, level, player.blockInteractionRange()) }
            hit?.blockPos == pos && hit.direction == facing
        }
        "Hybrid" -> {
            val packetHit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            if (packetHit?.blockPos == pos && packetHit.direction == facing) true
            else {
                val aim = placementAim
                val aimedHit = aim?.let { RayCastUtil.rayCastBlock(Rotation(it.rotation[0], it.rotation[1]), player, level, 4.5) }
                aimedHit?.blockPos == pos && aimedHit.direction == facing && abs(Mth.wrapDegrees(yaw - aim.rotation[0])) < 8f
            }
        }
        "Face", "Precise" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, player.blockInteractionRange())
            hit != null && hit.blockPos == pos && hit.direction == facing &&
                (rayCast.isMode("Face") || hit.location.distanceToSqr(player.eyePosition) <= 20.25)
        }
        "Reach", "Short Reach", "Eye" -> {
            val reach = if (rayCast.isMode("Short Reach")) 3.0 else player.blockInteractionRange()
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, reach)
            hit != null && hit.blockPos == pos && (rayCast.isMode("Reach") || hit.direction == facing)
        }
        "Previous" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            hit?.blockPos == pos && Vec3(lastX, lastY + player.eyeHeight, lastZ).distanceToSqr(hit.location) <= 20.25
        }
        "Dual", "Stable" -> {
            val current = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            val prior = cn.reverie.util.rotation.RotationManager.serverRotation?.let {
                RayCastUtil.rayCastBlock(it, player, level, 4.5)
            }
            current?.blockPos == pos && current.direction == facing &&
                (prior == null || prior.blockPos == pos && (rayCast.isMode("Dual") || prior.direction == facing))
        }
        "Permissive" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            hit?.blockPos == pos || abs(Mth.wrapDegrees(yaw - (rotations?.get(0) ?: yaw))) <= 12f
        }
        "Visible" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            hit?.blockPos == pos && hit.direction == facing
        }
        "Hanabi" -> {
            val hit = if (fast.value) RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, player.blockInteractionRange()) else Minecraft.getInstance().hitResult
            hit is BlockHitResult && hit.blockPos == pos
        }
        "Simple" -> {
            val target = rotations ?: RotationUtil.getRotations(getVec(pos, facing)) ?: return false
            abs(Mth.wrapDegrees(yaw - target[0])) <= 35.0f
        }
        "Normal" -> if (fast.value) RayCastUtil.rayCastBlock(Rotation(yaw, pitch), 4.5) == pos else RayCastUtil.rayCastBlock(4.5) == pos
        "Medium" -> RayCastUtil.rayCastBlock(Rotation(yaw, pitch), 4.5) == pos
        "New" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, 4.5)
            hit != null && hit.blockPos == pos && hit.direction == facing
        }
        "Grim" -> GrimRayCastUtil.canPlaceGrim(player, pos, facing, lastX, lastY, lastZ, yaw, pitch)
        "Strict" -> {
            val hit = RayCastUtil.rayCastBlock(Rotation(yaw, pitch), player, level, player.blockInteractionRange())
            hit != null && hit.type == HitResult.Type.BLOCK && hit.blockPos == pos && hit.direction == facing
        }
        else -> true
    }
}
