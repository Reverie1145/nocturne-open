package cn.reverie.util.rotation

import cn.reverie.util.network.ProtocolUtil
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.max
import kotlin.math.min
object GrimRayCastUtil {

    private const val EPSILON = 1.0000000116860974E-7
    private const val MOVEMENT_THRESHOLD = 0.03

    fun canPlaceGrim(
        player: LocalPlayer,
        pos: BlockPos,
        face: Direction,
        lastX: Double = player.xOld,
        lastY: Double = player.yOld,
        lastZ: Double = player.zOld,
        yaw: Float = player.yRot,
        pitch: Float = player.xRot
    ): Boolean {
        val box = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )
        val reach = player.blockInteractionRange()

        if (!checkPositionPlace(player, box, face, lastX, lastY, lastZ)) {
            return false
        }
        if (!checkPositionPlace(player, box, face, player.x, player.y, player.z)) {
            return false
        }

        val lookDirs = listOf(Rotation(yaw, pitch))
        if (!checkRotationPlace(player, box, lookDirs, reach, lastX, lastY, lastZ) &&
            !checkRotationPlace(player, box, lookDirs, reach, player.x, player.y, player.z)
        ) {
            return false
        }

        return true
    }

    /**
     * Client-side port of GrimAC's RotationPlace check (Grim 2.3.73,
     * checks/impl/scaffolding/RotationPlace.java - "Placed a block while not looking at it").
     *
     * Grim ray traces the placed-against block from every eye height the player could
     * currently have, with every look direction the server may still consider valid:
     *   - the rotation of the flying packet that follows the place (post-flying check),
     *   - that rotation combined with the previous flying packet's yaw,
     *   - the fully lagged (lastYaw, lastPitch) combination for 1.9+ clients.
     *
     * Only the post-flying scenario is enforced here: it is the check that actually
     * flags on Grim, while the pre-flying variant only runs once flagBuffer > 0 - and
     * the post-flying check passing every tick keeps that buffer at zero.
     */
    fun canPlaceRotationPlace(
        player: LocalPlayer,
        pos: BlockPos,
        upcomingYaw: Float = player.yRot,
        upcomingPitch: Float = player.xRot
    ): Boolean {
        // you don't send flying packets when spectating entities / riding
        if (player.isSpectator || player.isPassenger) return true

        val box = AABB(
            pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble(),
            pos.x + 1.0, pos.y + 1.0, pos.z + 1.0
        )

        // Rotation carried by the previous flying packet (Grim's lastYaw / lastPitch).
        val lastSent = RotationManager.lastSentRotation() ?: Rotation(upcomingYaw, upcomingPitch)

        // The flying packet sent right after this place carries the upcoming rotation.
        // OnTick scaffolds look back right after placing, so that packet actually
        // carries the player's real view instead of the silent aim.
        val upcoming = if (RotationProfile.isInstantRotation()) {
            Rotation(player.yRot, player.xRot)
        } else {
            Rotation(upcomingYaw, upcomingPitch)
        }

        // Post-flying: the check re-runs once the flying packet following the place arrives;
        // yaw is the rotation that packet carries, lastYaw the previously sent one.
        return didRayTraceHit(player, box, player.x, player.y, player.z, upcoming.yaw, upcoming.pitch, lastSent.yaw, lastSent.pitch)
    }

    /**
     * Faithful port of RotationPlace#didRayTraceHit. Ray traces the target block box from
     * every possible eye height with every possible look direction Grim allows.
     */
    private fun didRayTraceHit(
        player: LocalPlayer,
        box: AABB,
        posX: Double,
        posY: Double,
        posZ: Double,
        yaw: Float,
        pitch: Float,
        lastYaw: Float,
        lastPitch: Float
    ): Boolean {
        var possibleLookDirs = ArrayList<Rotation>()
        possibleLookDirs.add(Rotation(yaw, pitch))
        possibleLookDirs.add(Rotation(lastYaw, pitch))

        // 1.9+ players could be a tick behind because the server doesn't get skipped ticks
        if (!isOlderThan(ProtocolVersion.v1_9)) {
            possibleLookDirs.add(Rotation(lastYaw, lastPitch))
        }

        // 1.7 players do not have any of these issues! They are always on the latest look vector
        if (isOlderThan(ProtocolVersion.v1_8)) {
            possibleLookDirs = arrayListOf(Rotation(yaw, pitch))
        }

        val possibleEyeHeights = getGrimPossibleEyeHeights(player)

        // Start checking if player is in the block
        var minEyeHeight = Double.MAX_VALUE
        var maxEyeHeight = -Double.MAX_VALUE
        for (height in possibleEyeHeights) {
            minEyeHeight = min(minEyeHeight, height)
            maxEyeHeight = max(maxEyeHeight, height)
        }

        val eyePositions = AABB(
            posX, posY + minEyeHeight, posZ,
            posX, posY + maxEyeHeight, posZ
        ).inflate(getGrimMovementThreshold())

        // If the player is inside a block, then they can ray trace through the block and hit the other side of the block
        if (eyePositions.intersects(box)) {
            return true
        }

        val distance = player.blockInteractionRange()
        for (d in possibleEyeHeights) {
            for (lookDir in possibleLookDirs) {
                val origin = Vec3(posX, posY + d, posZ)
                val direction = RotationUtil.getVectorForRotation(lookDir.yaw, lookDir.pitch)
                val end = origin.add(direction.scale(distance))
                if (calculateIntercept(box, origin, end) != null) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Mirrors GrimPlayer#getPossibleEyeHeights (Grim 2.3.73) using the protocol version
     * the server sees us on:
     *   1.14+: standing [1.62, 1.27, 0.4] * scale, sneaking [1.27, 1.62, 0.4] * scale,
     *          elytra/swimming [0.4, 1.62, 1.27] * scale
     *   1.9-1.13: sneaking height is 1.54
     *   1.8-:     standing [1.62, 1.54], sneaking [1.54, 1.62]
     */
    private fun getGrimPossibleEyeHeights(player: LocalPlayer): DoubleArray {
        val version = ProtocolUtil.targetVersion()
        val sneaking = player.isCrouching
        val swimming = player.isSwimming || player.isFallFlying || player.isAutoSpinAttack

        return when {
            version == null || version.newerThanOrEqualTo(ProtocolVersion.v1_14) -> {
                val scale = player.scale.toDouble()
                when {
                    swimming -> doubleArrayOf(0.4 * scale, 1.62 * scale, 1.27 * scale)
                    sneaking -> doubleArrayOf(1.27 * scale, 1.62 * scale, 0.4 * scale)
                    else -> doubleArrayOf(1.62 * scale, 1.27 * scale, 0.4 * scale)
                }
            }
            version.newerThanOrEqualTo(ProtocolVersion.v1_9) -> when {
                swimming -> doubleArrayOf(0.4, 1.62, 1.54)
                sneaking -> doubleArrayOf(1.54, 1.62, 0.4)
                else -> doubleArrayOf(1.62, 1.54, 0.4)
            }
            else -> if (sneaking) doubleArrayOf(1.54, 1.62) else doubleArrayOf(1.62, 1.54)
        }
    }

    /**
     * Mirrors GrimPlayer#getMovementThreshold (Grim 2.3.73):
     * clients older than 1.18.2 can skip ticks, so Grim expands the eye position
     * box by 0.03; newer clients only send positions every tick, so 0.0002.
     */
    private fun getGrimMovementThreshold(): Double {
        val version = ProtocolUtil.targetVersion() ?: return 0.0002
        return if (version.olderThan(ProtocolVersion.v1_18_2)) 0.03 else 0.0002
    }

    private fun isOlderThan(version: ProtocolVersion): Boolean {
        val target = ProtocolUtil.targetVersion() ?: return false
        return target.olderThan(version)
    }

    fun checkPositionPlace(
        player: Player,
        box: AABB,
        face: Direction,
        posX: Double = player.x,
        posY: Double = player.y,
        posZ: Double = player.z
    ): Boolean {
        val eyeHeights = getPossibleEyeHeights(player)
        var minEyeHeight = Double.MAX_VALUE
        var maxEyeHeight = Double.MIN_VALUE
        for (h in eyeHeights) {
            minEyeHeight = min(minEyeHeight, h)
            maxEyeHeight = max(maxEyeHeight, h)
        }

        val eyeBox = AABB(
            posX, posY + minEyeHeight, posZ,
            posX, posY + maxEyeHeight, posZ
        )
        if (eyeBox.intersects(box)) {
            return true
        }

        val hidden = when (face) {
            Direction.NORTH -> posZ > box.minZ
            Direction.SOUTH -> posZ < box.maxZ
            Direction.EAST -> posX < box.maxX
            Direction.WEST -> posX > box.minX
            Direction.UP -> (posY + maxEyeHeight) < box.maxY
            Direction.DOWN -> (posY + minEyeHeight) > box.minY
        }
        return !hidden
    }

    fun checkRotationPlace(
        player: Player,
        box: AABB,
        lookDirs: List<Rotation>,
        reach: Double = player.blockInteractionRange(),
        posX: Double = player.x,
        posY: Double = player.y,
        posZ: Double = player.z
    ): Boolean {
        if (isPlayerInsideBlock(player, box, posX, posY, posZ)) {
            return true
        }

        val eyeHeights = getPossibleEyeHeights(player)
        for (d in eyeHeights) {
            val origin = Vec3(posX, posY + d, posZ)
            for (rot in lookDirs) {
                val dir = RotationUtil.getVectorForRotation(rot.yaw, rot.pitch)
                val end = origin.add(dir.scale(reach))
                val intercept = calculateIntercept(box, origin, end)
                if (intercept != null) {
                    return true
                }
            }
        }
        return false
    }

    fun isPlayerInsideBlock(
        player: Player,
        box: AABB,
        posX: Double = player.x,
        posY: Double = player.y,
        posZ: Double = player.z
    ): Boolean {
        val eyeHeights = getPossibleEyeHeights(player)
        var minEyeHeight = Double.MAX_VALUE
        var maxEyeHeight = Double.MIN_VALUE
        for (h in eyeHeights) {
            minEyeHeight = min(minEyeHeight, h)
            maxEyeHeight = max(maxEyeHeight, h)
        }
        val eyeBox = AABB(
            posX, posY + minEyeHeight, posZ,
            posX, posY + maxEyeHeight, posZ
        )
        return eyeBox.intersects(box)
    }

    fun calculateIntercept(box: AABB, origin: Vec3, end: Vec3): Pair<Vec3, Direction>? {
        var minX = getIntermediateWithXValue(origin, end, box.minX)
        var maxX = getIntermediateWithXValue(origin, end, box.maxX)
        var minY = getIntermediateWithYValue(origin, end, box.minY)
        var maxY = getIntermediateWithYValue(origin, end, box.maxY)
        var minZ = getIntermediateWithZValue(origin, end, box.minZ)
        var maxZ = getIntermediateWithZValue(origin, end, box.maxZ)

        if (!isVecInYZ(box, minX)) minX = null
        if (!isVecInYZ(box, maxX)) maxX = null
        if (!isVecInXZ(box, minY)) minY = null
        if (!isVecInXZ(box, maxY)) maxY = null
        if (!isVecInXY(box, minZ)) minZ = null
        if (!isVecInXY(box, maxZ)) maxZ = null

        var best: Vec3? = null
        var bestFace: Direction? = null

        if (minX != null) {
            best = minX
            bestFace = Direction.WEST
        }

        if (maxX != null && (best == null || origin.distanceToSqr(maxX) < origin.distanceToSqr(best))) {
            best = maxX
            bestFace = Direction.EAST
        }

        if (minY != null && (best == null || origin.distanceToSqr(minY) < origin.distanceToSqr(best))) {
            best = minY
            bestFace = Direction.DOWN
        }

        if (maxY != null && (best == null || origin.distanceToSqr(maxY) < origin.distanceToSqr(best))) {
            best = maxY
            bestFace = Direction.UP
        }

        if (minZ != null && (best == null || origin.distanceToSqr(minZ) < origin.distanceToSqr(best))) {
            best = minZ
            bestFace = Direction.NORTH
        }

        if (maxZ != null && (best == null || origin.distanceToSqr(maxZ) < origin.distanceToSqr(best))) {
            best = maxZ
            bestFace = Direction.SOUTH
        }

        return if (best != null && bestFace != null) Pair(best, bestFace) else null
    }

    private fun getIntermediateWithXValue(self: Vec3, other: Vec3, x: Double): Vec3? {
        val deltaX = other.x - self.x
        val deltaY = other.y - self.y
        val deltaZ = other.z - self.z
        if (deltaX * deltaX < EPSILON) return null
        val d3 = (x - self.x) / deltaX
        return if (d3 in 0.0..1.0) Vec3(self.x + deltaX * d3, self.y + deltaY * d3, self.z + deltaZ * d3) else null
    }

    private fun getIntermediateWithYValue(self: Vec3, other: Vec3, y: Double): Vec3? {
        val deltaX = other.x - self.x
        val deltaY = other.y - self.y
        val deltaZ = other.z - self.z
        if (deltaY * deltaY < EPSILON) return null
        val d3 = (y - self.y) / deltaY
        return if (d3 in 0.0..1.0) Vec3(self.x + deltaX * d3, self.y + deltaY * d3, self.z + deltaZ * d3) else null
    }

    private fun getIntermediateWithZValue(self: Vec3, other: Vec3, z: Double): Vec3? {
        val deltaX = other.x - self.x
        val deltaY = other.y - self.y
        val deltaZ = other.z - self.z
        if (deltaZ * deltaZ < EPSILON) return null
        val d3 = (z - self.z) / deltaZ
        return if (d3 in 0.0..1.0) Vec3(self.x + deltaX * d3, self.y + deltaY * d3, self.z + deltaZ * d3) else null
    }

    private fun isVecInYZ(box: AABB, vec: Vec3?): Boolean {
        return vec != null && vec.y >= box.minY && vec.y <= box.maxY && vec.z >= box.minZ && vec.z <= box.maxZ
    }

    private fun isVecInXZ(box: AABB, vec: Vec3?): Boolean {
        return vec != null && vec.x >= box.minX && vec.x <= box.maxX && vec.z >= box.minZ && vec.z <= box.maxZ
    }

    private fun isVecInXY(box: AABB, vec: Vec3?): Boolean {
        return vec != null && vec.x >= box.minX && vec.x <= box.maxX && vec.y >= box.minY && vec.y <= box.maxY
    }

    fun getPossibleEyeHeights(player: Player): DoubleArray {
        val current = player.getEyeHeight(player.pose).toDouble()
        val isSneaking = player.isCrouching
        return if (isSneaking) {
            doubleArrayOf(current, 1.27, 1.62, 0.4)
        } else {
            doubleArrayOf(current, 1.62, 1.27, 0.4)
        }
    }

    fun getPossibleRotations(player: LocalPlayer): List<Rotation> {
        val list = ArrayList<Rotation>()
        RotationManager.currentRotation?.let { list.add(it) }
        RotationManager.targetRotation?.let { list.add(it) }
        RotationManager.serverRotation?.let { list.add(it) }
        RotationManager.currentRotations?.let { list.add(Rotation(it[0], it[1])) }
        RotationManager.targetRotations?.let { list.add(Rotation(it[0], it[1])) }
        RotationManager.lastRotations?.let { list.add(Rotation(it[0], it[1])) }
        RotationProfile.scaffoldRotations()?.let { list.add(Rotation(it[0], it[1])) }
        list.add(Rotation(player.yRot, player.xRot))
        list.add(Rotation(player.yHeadRot, player.xRot))

        val lastYaw = RotationManager.serverRotation?.yaw
            ?: RotationManager.lastRotations?.get(0)
            ?: player.yRotO
        val lastPitch = RotationManager.serverRotation?.pitch
            ?: RotationManager.lastRotations?.get(1)
            ?: player.xRotO
        val curYaw = RotationManager.currentRotation?.yaw
            ?: RotationManager.currentRotations?.get(0)
            ?: player.yRot
        val curPitch = RotationManager.currentRotation?.pitch
            ?: RotationManager.currentRotations?.get(1)
            ?: player.xRot

        list.add(Rotation(lastYaw, curPitch))
        list.add(Rotation(lastYaw, lastPitch))

        return list.distinctBy { Pair(it.yaw, it.pitch) }
    }
}
