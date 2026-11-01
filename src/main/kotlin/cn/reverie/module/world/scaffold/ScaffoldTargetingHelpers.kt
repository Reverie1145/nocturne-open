package cn.reverie.module.world.scaffold

import cn.reverie.module.world.Scaffold
import cn.reverie.util.player.BlockUtil
import cn.reverie.util.rotation.RayCastUtil
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.level.ClipContext
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import cn.reverie.util.player.PlayerUtil
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

internal data class ScaffoldAim(val rotation: FloatArray, val hitVec: Vec3)

internal fun Scaffold.getYLevelImpl(player: LocalPlayer? = Minecraft.getInstance().player): Double =
    player?.let { getPlaceY(it).toDouble() } ?: 0.0

internal fun Scaffold.getBlockDataImpl(player: LocalPlayer?, belowBlockPos: BlockPos): Scaffold.PlaceInfo? {
    if (Minecraft.getInstance().level == null) return null
    val p = player ?: return null
    return if (searchMode.isMode("Distance")) {
        searchByDistance(p, belowBlockPos)
    } else {
        searchNormal(belowBlockPos)
    }
}

private val normalSearchDirections = arrayOf(
    Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP
)

private data class SearchNode(val pos: BlockPos, val depth: Int)

private fun Scaffold.searchNormal(origin: BlockPos): Scaffold.PlaceInfo? {
    val level = Minecraft.getInstance().level ?: return null
    val queue = ArrayDeque<SearchNode>()
    for (face in normalSearchDirections) {
        val neighbor = origin.relative(face.opposite)
        if (isAttachable(neighbor)) return Scaffold.PlaceInfo(neighbor, face)
        queue.addLast(SearchNode(neighbor, 1))
    }

    while (queue.isNotEmpty()) {
        val (current, depth) = queue.removeFirst()
        if (depth >= 4) continue
        for (face in normalSearchDirections) {
            val next = current.relative(face.opposite)
            if (isAttachable(next)) return Scaffold.PlaceInfo(next, face)
            queue.addLast(SearchNode(next, depth + 1))
        }
    }

    for (distance in 1..2) {
        val below = origin.below(distance)
        if (isAttachable(below)) return Scaffold.PlaceInfo(below, Direction.UP)
    }
    return null
}

private fun Scaffold.searchByDistance(player: LocalPlayer, origin: BlockPos): Scaffold.PlaceInfo? {
    val level = Minecraft.getInstance().level ?: return null
    val feet = Vec3(player.x, player.y, player.z)
    val baseY = origin.y
    val playerX = floor(feet.x).toInt()
    val playerZ = floor(feet.z).toInt()
    var bestPos: BlockPos? = null
    var bestScore = Double.MAX_VALUE

    for (x in playerX - 5..playerX + 5) {
        for (y in baseY - 1..baseY) {
            for (z in playerZ - 5..playerZ + 5) {
                val pos = BlockPos(x, y, z)
                val state = level.getBlockState(pos)
                val shape = state.getCollisionShape(level, pos)
                if (shape.isEmpty || state.isInteractableForScaffold()) continue
                val box = shape.bounds()
                val nearestX = feet.x.coerceIn(pos.x + box.minX, pos.x + box.maxX)
                val nearestY = feet.y.coerceIn(pos.y + box.minY, pos.y + box.maxY)
                val nearestZ = feet.z.coerceIn(pos.z + box.minZ, pos.z + box.maxZ)
                val dx = feet.x - nearestX
                val dy = feet.y - nearestY
                val dz = feet.z - nearestZ
                val score = dx * dx + dy * dy + dz * dz
                if (score < bestScore) {
                    bestScore = score
                    bestPos = pos
                }
            }
        }
    }

    val target = bestPos ?: return null
    val facing = getPlaceSide(target, baseY, feet) ?: return null
    return Scaffold.PlaceInfo(target, facing)
}

private fun Scaffold.getPlaceSide(blockPos: BlockPos, baseY: Int, feet: Vec3): Direction? {
    val player = Minecraft.getInstance().player ?: return null
    val offsets = arrayOf(
        intArrayOf(0, 1, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
        intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
    )
    val facings = arrayOf(Direction.UP, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)
    val px = floor(feet.x).toInt()
    val py = baseY + 1
    val pz = floor(feet.z).toInt()
    val jumping = !player.onGround() && Minecraft.getInstance().options.keyJump.isDown
    var bestDistance = Double.MAX_VALUE
    var bestFacing: Direction? = null

    for (i in offsets.indices) {
        if (i == 0 && !jumping) continue
        val candidate = blockPos.offset(offsets[i][0], offsets[i][1], offsets[i][2])
        if (candidate.x == px && candidate.y == py && candidate.z == pz) continue
        val facing = facings[i]
        if (!canPlaceAt(candidate)) continue
        if (i == 0 && !checkBlock2(player, candidate, facing)) continue
        if (!canPlaceBlockOnSide(player, candidate, facing)) continue
        val hit = bestFeetHit(candidate, feet)
        val dx = feet.x - hit.x
        val dy = feet.y - hit.y
        val dz = feet.z - hit.z
        val distance = dx * dx + dy * dy + dz * dz
        if (distance < bestDistance) {
            bestDistance = distance
            bestFacing = facing
        }
    }

    if (bestFacing == null) return null
    val currentHit = bestFeetHit(blockPos, feet)
    val dx = feet.x - currentHit.x
    val dy = feet.y - currentHit.y
    val dz = feet.z - currentHit.z
    val currentDistance = dx * dx + dy * dy + dz * dz
    return bestFacing.takeIf { bestDistance < currentDistance }
}

private fun checkBlock2(player: LocalPlayer, pos: BlockPos, facing: Direction): Boolean {
    val direction = facing.stepX to (facing.stepY to facing.stepZ)
    val hit = Vec3.atCenterOf(pos).add(direction.first * 0.5, direction.second.first * 0.5, direction.second.second * 0.5)
    val relevant = hit.subtract(player.eyePosition)
    val faceNormal = Vec3(-direction.first.toDouble(), -direction.second.first.toDouble(), -direction.second.second.toDouble())
    return relevant.lengthSqr() <= 20.25 && relevant.normalize().dot(faceNormal.normalize()) >= 0.0
}

private fun Scaffold.bestFeetHit(pos: BlockPos, feet: Vec3): Vec3 {
    val level = Minecraft.getInstance().level ?: return Vec3.atCenterOf(pos)
    val shape = level.getBlockState(pos).getCollisionShape(level, pos)
    if (shape.isEmpty) {
        return Vec3(
            feet.x.coerceIn(pos.x + 0.05, pos.x + 0.95),
            feet.y.coerceIn(pos.y - 0.05, pos.y + 1.05),
            feet.z.coerceIn(pos.z + 0.05, pos.z + 0.95)
        )
    }
    val box = shape.bounds()
    return Vec3(
        feet.x.coerceIn(pos.x + box.minX + 0.05, pos.x + box.maxX - 0.05),
        feet.y.coerceIn(pos.y + box.minY - 0.05, pos.y + box.maxY + 0.05),
        feet.z.coerceIn(pos.z + box.minZ + 0.05, pos.z + box.maxZ - 0.05)
    )
}

private fun BlockState.isInteractableForScaffold(): Boolean {
    val block = block
    return hasBlockEntity() || block is CraftingTableBlock || block is AnvilBlock || block is BedBlock ||
        block is DoorBlock || block is TrapDoorBlock || block is FenceGateBlock || block is FenceBlock ||
        block is ButtonBlock || block is LeverBlock || block is JukeboxBlock
}

private fun canPlaceBlockOnSide(player: LocalPlayer, pos: BlockPos, side: Direction): Boolean {
    val hand = if (player.mainHandItem.item is BlockItem) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND
    val stack = if (hand == InteractionHand.MAIN_HAND) player.mainHandItem else player.offhandItem
    if (stack.isEmpty) return false
    val blockItem = stack.item as? BlockItem ?: return false
    val hit = BlockHitResult(Vec3.atCenterOf(pos), side, pos, false)
    val context = BlockPlaceContext(player, hand, stack, hit)
    return blockItem.block.getStateForPlacement(context) != null
}

private fun Scaffold.isAttachable(pos: BlockPos): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    val state = level.getBlockState(pos)
    return !state.isAir && !state.getCollisionShape(level, pos).isEmpty
}

private fun Scaffold.canPlaceAt(pos: BlockPos): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    val state = level.getBlockState(pos)
    if (state.block is SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) <= 1) return true
    return state.isAir || state.canBeReplaced()
}

internal fun Scaffold.getVecImpl(pos: BlockPos, facing: Direction): Vec3 = Vec3(
    pos.x + 0.5 + facing.stepX * 0.5,
    pos.y + 0.5 + facing.stepY * 0.5,
    pos.z + 0.5 + facing.stepZ * 0.5
)

internal fun Scaffold.getRotationSpeedImpl(player: LocalPlayer? = Minecraft.getInstance().player): Int {
    val profile = mixProfileIndex()
    if (profile >= 0) return ScaffoldRotationProfiles.speed(profile, player?.tickCount ?: 0, profileTunings[profile].value)
    return when (rotationMode.value) {
        "Smooth" -> (smoothRotationSpeed.value / 10.0 * 180.0).roundToInt()
        "Hypixel", "Watchdog" -> 170
        "Heypixel" -> 180
        "Focus" -> 35
        "Human" -> 55 + (player?.tickCount ?: 0) % 17
        "Cautious" -> 30
        "Glide" -> 65
        "Pulse" -> if ((player?.tickCount ?: 0) % 4 == 0) 120 else 30
        else -> rotationSpeed.getInt()
    }
}

/** Advances the shared Telly rotation state used by Never's Hypixel and Heypixel modes. */
internal fun Scaffold.updateNeverRotationState(player: LocalPlayer) {
    if (!isNeverRotationMode()) return

    if (player.onGround() && PlayerUtil.onGroundTicks <= 1) {
        val normalizedYaw = Mth.wrapDegrees(player.yRot - neverHardOffset())
            .let { ((it % 360f) + 360f) % 360f }
        neverYawNormal = normalizedYaw % 90f < 45f
    }
    if (neverEnabledOnAir && PlayerUtil.onGroundTicks > 0) neverEnabledOnAir = false

    if (!mode.isMode("Telly")) return
    if (neverRotationTick > 0) neverRotationTick--
    if (player.onGround()) {
        if (neverStage > 0) neverStage--
        if (neverStage < 0) neverStage++
        if (neverStage == 0 && !keepY.value && !Minecraft.getInstance().options.keyJump.isDown) {
            neverStage = 1
        }
        neverCanRotate = false
        neverRotationTick = 0
    }
}

/** Never's rotation modes adapted to Nocturne's target/movement-fix manager. */
internal fun Scaffold.getNeverRotation(player: LocalPlayer, targetRotation: FloatArray): FloatArray {
    val usesHypixelTurn = rotationMode.isMode("Hypixel") || rotationMode.isMode("Watchdog")
    val lastRotation = neverLastRotation?.clone()
        ?: if (usesHypixelTurn) {
            RotationManager.serverRotation?.let { floatArrayOf(it.yaw, it.pitch) }
                ?: RotationUtil.getRotationOrElseMC().let { floatArrayOf(it.yaw, it.pitch) }
        } else {
            RotationUtil.getRotationOrElseMC().let { floatArrayOf(it.yaw, it.pitch) }
        }

    val rotation = targetRotation.clone()
    if (mode.isMode("Telly")) {
        if (usesHypixelTurn) {
            if (neverCanRotate && (player.deltaMovement.y > 0.0 || player.y > placeY + 1.0)) {
                val speed = when {
                    neverEnabledOnAir -> if (offGroundTicks <= 1) 100f else 40f
                    slow.value -> if (offGroundTicks <= 1) 90f else 47f
                    offGroundTicks <= 1 -> 147f
                    else -> 45f
                }
                val deltaYaw = Mth.wrapDegrees(rotation[0] - lastRotation[0])
                if (abs(deltaYaw) > speed) {
                    rotation[0] = quantizeNeverYaw(lastRotation[0] + deltaYaw.coerceIn(-speed, speed))
                    neverRotationTick = 0
                }
            }

            val options = Minecraft.getInstance().options
            val hasMovementKey = options.keyUp.isDown != options.keyDown.isDown ||
                options.keyLeft.isDown != options.keyRight.isDown
            if (player.onGround() && hasMovementKey && isAirAbove(player)) {
                val hardOffset = neverHardOffset()
                val shouldTurn = neverStage > 0 || (!keepY.value && options.keyJump.isDown)
                if (shouldTurn && PlayerUtil.onGroundTicks >= jumpDelayMin.getInt()) {
                    val side = Mth.wrapDegrees(lastRotation[0] - player.yRot - hardOffset) > 0f
                    val sign = if (isNeverDiagonal(player)) {
                        if (neverYawNormal) 1f else -1f
                    } else if (side) {
                        1f
                    } else {
                        -1f
                    }
                    rotation[0] = player.yRot - hardOffset + sign * 157f
                    rotation[1] = 80f
                    neverRotationTick = 0
                    neverCanRotate = true
                }
            }
        } else {
            if (neverCanRotate && (player.deltaMovement.y > 0.0 || player.y > placeY + 1.0)) {
                val speed = if (offGroundTicks <= 1) 107f else 39f
                val deltaYaw = Mth.wrapDegrees(rotation[0] - lastRotation[0])
                if (abs(deltaYaw) > speed) {
                    rotation[0] = quantizeNeverYaw(lastRotation[0] + deltaYaw.coerceIn(-speed, speed))
                    neverRotationTick = 0
                }
            }

            val options = Minecraft.getInstance().options
            val hasMovementKey = options.keyUp.isDown != options.keyDown.isDown ||
                options.keyLeft.isDown != options.keyRight.isDown
            if (player.onGround() && hasMovementKey && isAirAbove(player) &&
                (neverStage > 0 || (!keepY.value && options.keyJump.isDown))) {
                rotation[0] = RotationUtil.getRotationOrElseMC().yaw
                rotation[1] = RotationUtil.getRotationOrElseMC().pitch
                neverRotationTick = 3
                neverCanRotate = true
            }
        }
    }

    neverLastRotation = rotation.clone()
    return rotation
}

private fun neverHardOffset(): Float {
    val mc = Minecraft.getInstance()
    if (mc.gui.screen() != null) return 180f

    val options = mc.options
    val forward = options.keyUp.isDown
    val back = options.keyDown.isDown
    val left = options.keyLeft.isDown
    val right = options.keyRight.isDown
    val duplicate = left && right
    var offset = 0f

    if (forward) {
        offset -= 180f
        if (!duplicate) {
            if (left) offset += 45f
            if (right) offset -= 45f
        }
    } else if (!back) {
        offset -= 180f
        if (!duplicate) {
            if (left) offset += 90f
            if (right) offset -= 90f
        }
    } else if (!duplicate) {
        if (left) offset -= 45f
        if (right) offset += 45f
    }
    return offset
}

private fun isNeverDiagonal(player: LocalPlayer): Boolean =
    abs(player.deltaMovement.x) > 0.07 && abs(player.deltaMovement.z) > 0.07

private fun isAirAbove(player: LocalPlayer): Boolean {
    val level = Minecraft.getInstance().level ?: return false
    return !level.getBlockCollisions(player, player.boundingBox.move(0.0, 1.0, 0.0)).iterator().hasNext()
}

private fun quantizeNeverYaw(yaw: Float): Float = yaw - yaw % 0.0096f

internal fun Scaffold.getBlockSlotImpl(): Int {
    val player = Minecraft.getInstance().player ?: return -1
    var bestSlot = -1
    var bestCount = -1
    for (slot in 0..8) {
        val stack = player.inventory.getItem(slot)
        if (!isValidBlock(stack)) continue
        if (!switchMode.isMode("Biggest") && !switchMode.isMode("Threshold")) return slot
        if (stack.count > bestCount) {
            bestCount = stack.count
            bestSlot = slot
        }
    }
    return bestSlot
}

internal fun Scaffold.isValidBlockStack(stack: net.minecraft.world.item.ItemStack): Boolean {
    val item = stack.item as? net.minecraft.world.item.BlockItem ?: return false
    val block = item.block
    if (!BlockUtil.isPlaceable(block)) return false
    return block !is net.minecraft.world.level.block.SlabBlock && block !is net.minecraft.world.level.block.StairBlock
}

internal fun Scaffold.isOmniMovingImpl(): Boolean =
    isOmniSprintEnabled() && !omniSuppressed && (omniRawForward != 0.0f || omniRawStrafe != 0.0f)

internal fun Scaffold.getPlacementAim(
    player: LocalPlayer,
    placeInfo: Scaffold.PlaceInfo,
    reference: Rotation
): ScaffoldAim? {
    val faceAim = aimAt(player, placeInfo, getVec(placeInfo.blockPos, placeInfo.facing))
    val profile = mixProfileIndex()
    val selectedAim = if (profile >= 0) {
        faceCoordinatesAim(player, placeInfo, reference, ScaffoldRotationProfiles.point(profile, player.tickCount, profileTunings[profile].value))
    } else when (rotationMode.value) {
        "Facing" -> aimAt(player, placeInfo, getVec(placeInfo.blockPos, placeInfo.facing))
        "Hit Vec" -> aimAt(player, placeInfo, getVec(placeInfo.blockPos, placeInfo.facing))
        "Nearest" -> nearestSurfaceAim(player, placeInfo, reference, shrink.value)
        "Focus" -> facePatternAim(player, placeInfo, reference, "Center")
        "Human" -> facePatternAim(player, placeInfo, reference, "Stable Random")
        "Cautious" -> facePatternAim(player, placeInfo, reference, "Inner")
        "Glide" -> facePatternAim(player, placeInfo, reference, "Forward")
        "Pulse" -> facePatternAim(player, placeInfo, reference, "Sweep")
        else -> when (rotationPoint.value) {
            "Corner" -> cornerAim(player, placeInfo, reference)
            "Nearest" -> nearestSurfaceAim(player, placeInfo, reference, 0.0)
            "Reduced" -> reducedAim(player, placeInfo, reference)
            "Center", "Inner", "Outer", "Forward", "Backward", "Left", "Right", "Top", "Bottom", "Random", "Stable Random", "Adaptive", "Near Eye", "Far Eye", "Sweep", "Orbit", "Diagonal", "Cross" ->
                facePatternAim(player, placeInfo, reference, rotationPoint.value)
            else -> normalAim(player, placeInfo, reference)
        }
    }

    // The sampled/raycast-based strategies can have no candidate for a tick
    // (for example Reduced when all sampled points miss). Never falls back to
    // the block face in that case; without this, the rotation hook receives
    // null and the mode appears to stop turning entirely.
    return (selectedAim ?: faceAim)?.let { applyRotationNoise(player, placeInfo, it) }
}

private fun Scaffold.applyRotationNoise(player: LocalPlayer, info: Scaffold.PlaceInfo, aim: ScaffoldAim): ScaffoldAim {
    if (rotationNoise.isMode("Off") || noiseAmount.value <= 0.0) return aim
    val tick = player.tickCount.toDouble()
    val period = noiseInterval.value.coerceAtLeast(2.0)
    val phase = tick / period
    fun hash(index: Long): Double {
        val bits = index * 6364136223846793005L + 1442695040888963407L
        return ((bits ushr 33).toDouble() / 2147483647.0) * 2.0 - 1.0
    }
    fun sample(offset: Long): Double = when (rotationNoise.value) {
        "White" -> hash(tick.toLong() + offset)
        "Smooth" -> {
            val base = kotlin.math.floor(phase).toLong()
            val fraction = phase - kotlin.math.floor(phase)
            val smooth = fraction * fraction * (3.0 - 2.0 * fraction)
            hash(base + offset) * (1.0 - smooth) + hash(base + offset + 1) * smooth
        }
        "Sine" -> sin(phase * Math.PI * 2.0 + offset)
        "Triangle" -> 1.0 - 4.0 * kotlin.math.abs((phase + offset * 0.25) % 1.0 - 0.5)
        "Drift" -> sin(phase * 2.0 + offset) * 0.7 + sin(phase * 0.73 + offset) * 0.3
        "Micro" -> hash((tick / 3.0).toLong() + offset) * 0.3
        else -> 0.0
    }
    val yaw = aim.rotation[0] + (sample(0) * noiseAmount.value).toFloat()
    val pitch = (aim.rotation[1] + (sample(129) * noiseAmount.value).toFloat()).coerceIn(-90f, 90f)
    val rotation = floatArrayOf(yaw, pitch)
    val hit = raycastRotation(player, rotation, player.blockInteractionRange())
    return if (hit?.blockPos == info.blockPos && hit.direction == info.facing) ScaffoldAim(rotation, hit.location) else aim
}

private fun Scaffold.facePatternAim(player: LocalPlayer, info: Scaffold.PlaceInfo, reference: Rotation, pattern: String): ScaffoldAim? {
    val seed = info.blockPos.asLong() xor (player.tickCount / 8).toLong()
    val random = java.util.Random(if (pattern == "Stable Random") info.blockPos.asLong() else seed)
    val eye = player.eyePosition
    val center = Vec3.atCenterOf(info.blockPos)
    val distance = eye.distanceTo(center)
    val phase = player.tickCount * 0.18
    val offset = when (pattern) {
        "Inner" -> 0.18 to 0.18
        "Outer" -> 0.82 to 0.82
        "Forward" -> 0.5 to 0.78
        "Backward" -> 0.5 to 0.22
        "Left" -> 0.22 to 0.5
        "Right" -> 0.78 to 0.5
        "Top" -> 0.5 to 0.82
        "Bottom" -> 0.5 to 0.18
        "Random", "Stable Random" -> random.nextDouble(0.18, 0.82) to random.nextDouble(0.18, 0.82)
        "Adaptive" -> if (distance > 3.0) 0.5 to 0.5 else 0.38 to 0.62
        "Near Eye", "Far Eye" -> {
            val horizontal = if (info.facing.axis == Direction.Axis.X) eye.z - info.blockPos.z else eye.x - info.blockPos.x
            val vertical = if (info.facing.axis == Direction.Axis.Y) eye.z - info.blockPos.z else eye.y - info.blockPos.y
            val near = horizontal.coerceIn(0.18, 0.82) to vertical.coerceIn(0.18, 0.82)
            if (pattern == "Near Eye") near else (1.0 - near.first) to (1.0 - near.second)
        }
        "Sweep" -> (0.5 + sin(phase) * 0.3) to 0.5
        "Orbit" -> (0.5 + cos(phase) * 0.25) to (0.5 + sin(phase) * 0.25)
        "Diagonal" -> (0.5 + sin(phase) * 0.25) to (0.5 + sin(phase) * 0.25)
        "Cross" -> if (player.tickCount % 12 < 6) (0.5 + sin(phase) * 0.25) to 0.5 else 0.5 to (0.5 + sin(phase) * 0.25)
        else -> 0.5 to 0.5
    }
    val (u, v) = offset
    return faceCoordinatesAim(player, info, reference, u to v)
}

private fun Scaffold.faceCoordinatesAim(player: LocalPlayer, info: Scaffold.PlaceInfo, reference: Rotation, offset: Pair<Double, Double>): ScaffoldAim? {
    val eye = player.eyePosition
    val (u, v) = offset
    val point = when (info.facing) {
        Direction.UP -> Vec3(info.blockPos.x + u, info.blockPos.y + 1.0, info.blockPos.z + v)
        Direction.DOWN -> Vec3(info.blockPos.x + u, info.blockPos.y.toDouble(), info.blockPos.z + v)
        Direction.NORTH -> Vec3(info.blockPos.x + u, info.blockPos.y + v, info.blockPos.z.toDouble())
        Direction.SOUTH -> Vec3(info.blockPos.x + u, info.blockPos.y + v, info.blockPos.z + 1.0)
        Direction.WEST -> Vec3(info.blockPos.x.toDouble(), info.blockPos.y + v, info.blockPos.z + u)
        Direction.EAST -> Vec3(info.blockPos.x + 1.0, info.blockPos.y + v, info.blockPos.z + u)
    }
    val rotation = RotationUtil.getRotations(eye, point)
    val hit = raycastRotation(player, rotation, player.blockInteractionRange())
    return if (hit?.blockPos == info.blockPos && hit.direction == info.facing) ScaffoldAim(rotation, hit.location)
        else nearestSurfaceAim(player, info, reference, 0.1)
}

private fun aimAt(player: LocalPlayer, info: Scaffold.PlaceInfo, point: Vec3): ScaffoldAim? {
    val rotations = RotationUtil.getRotations(player.eyePosition, point)
    return ScaffoldAim(rotations, point).takeIf { point.distanceToSqr(player.eyePosition) <= player.blockInteractionRange() * player.blockInteractionRange() }
}

private fun Scaffold.normalAim(player: LocalPlayer, info: Scaffold.PlaceInfo, reference: Rotation): ScaffoldAim? {
    val pos = info.blockPos
    val points = arrayOf(
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5),
        Vec3(pos.x + 0.5, pos.y + 1.001, pos.z + 0.5),
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z - 0.001),
        Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 1.001),
        Vec3(pos.x - 0.001, pos.y + 0.5, pos.z + 0.5),
        Vec3(pos.x + 1.001, pos.y + 0.5, pos.z + 0.5)
    )
    for (point in points) {
        val hit = raycastPoint(player, point, player.blockInteractionRange()) ?: continue
        if (hit.blockPos == pos) return aimAt(player, info, point)
    }
    return nearestSurfaceAim(player, info, reference, 0.0)
}

private fun Scaffold.cornerAim(player: LocalPlayer, info: Scaffold.PlaceInfo, reference: Rotation): ScaffoldAim? {
    val pos = info.blockPos
    val inset = 0.15
    val yaw = ((reference.yaw % 360f) + 360f) % 360f
    val (a, b) = when {
        yaw >= 315f || yaw < 45f -> Vec3(pos.x + 1 - inset, pos.y + 0.2, pos.z + inset) to Vec3(pos.x + 1 - inset, pos.y + 0.2, pos.z + 1 - inset)
        yaw < 135f -> Vec3(pos.x + inset, pos.y + 0.2, pos.z + 1 - inset) to Vec3(pos.x + 1 - inset, pos.y + 0.2, pos.z + 1 - inset)
        yaw < 225f -> Vec3(pos.x + inset, pos.y + 0.2, pos.z + inset) to Vec3(pos.x + inset, pos.y + 0.2, pos.z + 1 - inset)
        else -> Vec3(pos.x + inset, pos.y + 0.2, pos.z + inset) to Vec3(pos.x + 1 - inset, pos.y + 0.2, pos.z + inset)
    }
    return aimAt(player, info, if (player.eyePosition.distanceToSqr(a) < player.eyePosition.distanceToSqr(b)) a else b)
}

private fun Scaffold.nearestSurfaceAim(
    player: LocalPlayer,
    info: Scaffold.PlaceInfo,
    reference: Rotation,
    inset: Double
): ScaffoldAim? {
    val offsets = (0..15).map { 0.03125 + it * 0.0625 }
    val xs = if (info.facing.axis == Direction.Axis.X) listOf(if (info.facing == Direction.EAST) 1.0 else 0.0) else offsets.map { inset + it * (1.0 - 2.0 * inset) }
    val ys = if (info.facing.axis == Direction.Axis.Y) listOf(if (info.facing == Direction.UP) 1.0 else 0.0) else offsets.map { inset + it * (1.0 - 2.0 * inset) }
    val zs = if (info.facing.axis == Direction.Axis.Z) listOf(if (info.facing == Direction.SOUTH) 1.0 else 0.0) else offsets.map { inset + it * (1.0 - 2.0 * inset) }
    var best: ScaffoldAim? = null
    var bestCost = Double.MAX_VALUE
    for (x in xs) for (y in ys) for (z in zs) {
        val point = Vec3(info.blockPos.x + x, info.blockPos.y + y, info.blockPos.z + z)
        val rotation = RotationUtil.getRotations(player.eyePosition, point)
        val hit = raycastRotation(player, rotation, player.blockInteractionRange()) ?: continue
        if (hit.blockPos != info.blockPos) continue
        val cost = rotationCost(reference, rotation)
        if (cost < bestCost) {
            bestCost = cost
            best = ScaffoldAim(rotation, hit.location)
        }
    }
    return best
}

private data class RotationCandidate(val cost: Double, val rotation: FloatArray)

private fun Scaffold.reducedAim(player: LocalPlayer, info: Scaffold.PlaceInfo, reference: Rotation): ScaffoldAim? {
    val step = searchStep.value.coerceIn(0.01, 0.1)
    val n = (1.0 / step).roundToInt().coerceIn(1, 100)
    val eye = player.eyePosition
    val pos = info.blockPos
    val insetValue = inset.value.coerceIn(0.0, 0.49)
    val jitterSize = step * 0.2
    val candidates = ArrayList<RotationCandidate>((n + 1) * (n + 1) * 3 + 1)
    candidates += RotationCandidate(0.0, floatArrayOf(reference.yaw, reference.pitch))
    val faceUp = abs(eye.y - (pos.y + 1)) < abs(eye.y - pos.y)
    val faceSouth = abs(eye.z - (pos.z + 1)) < abs(eye.z - pos.z)
    val faceEast = abs(eye.x - (pos.x + 1)) < abs(eye.x - pos.x)

    for (r in 0..n) {
        val v = (r * step + randomJitter(jitterSize)).coerceIn(0.0, 1.0)
        for (c in 0..n) {
            val u = (c * step + randomJitter(jitterSize)).coerceIn(0.0, 1.0)
            val points = arrayOf(
                Vec3(pos.x + u, pos.y + if (faceUp) 1.0 - insetValue else insetValue, pos.z + v),
                Vec3(pos.x + u, pos.y + v, pos.z + if (faceSouth) 1.0 - insetValue else insetValue),
                Vec3(pos.x + if (faceEast) 1.0 - insetValue else insetValue, pos.y + v, pos.z + u)
            )
            for (point in points) {
                val rotation = RotationUtil.getRotations(eye, point)
                candidates += RotationCandidate(rotationCost(reference, rotation), rotation)
            }
        }
    }
    candidates.sortBy { it.cost }
    val found = ArrayList<ScaffoldAim>(11)
    for (candidate in candidates) {
        val rotation = candidate.rotation
        val hit = raycastRotation(player, rotation, player.blockInteractionRange()) ?: continue
        if (hit.blockPos != pos || hit.direction != info.facing) continue
        if (!canPlaceBlockOnSide(player, hit.blockPos, hit.direction)) continue
        found += ScaffoldAim(rotation, hit.location)
        if (found.size > if (jitter.value) 10 else 0) break
    }
    if (found.isEmpty()) return null
    return if (jitter.value) found.shuffled().first() else found.first()
}

private fun randomJitter(max: Double): Double = if (max == 0.0) 0.0 else Random.nextDouble(-max, max)

private fun raycastPoint(player: LocalPlayer, point: Vec3, reach: Double): BlockHitResult? {
    val level = Minecraft.getInstance().level ?: return null
    if (player.eyePosition.distanceToSqr(point) > reach * reach) return null
    return level.clip(ClipContext(player.eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
}

private fun raycastRotation(player: LocalPlayer, rotation: FloatArray, reach: Double): BlockHitResult? =
    RayCastUtil.rayCastBlock(Rotation(rotation[0], rotation[1]), player, player.level(), reach)

private fun rotationCost(base: Rotation, target: FloatArray): Double =
    abs(Mth.wrapDegrees(target[0] - base.yaw)).toDouble() + abs(target[1] - base.pitch).toDouble()
