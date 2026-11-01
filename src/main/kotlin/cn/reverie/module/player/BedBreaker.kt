package cn.reverie.module.player

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.player.bedteam.BedTeamTracker
import cn.reverie.module.world.Scaffold
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.render.nanovg.NanoVGRenderer
import cn.reverie.render.theme.Theme
import cn.reverie.util.render.Render3DUtil
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

object BedBreaker : Module(
    name = "BedBreaker",
    description = "Automatically destroys nearby beds",
    category = Category.PLAYER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    private val searchDirections = Direction.values()
    private val directBreakFaces = arrayOf(
        Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    )

    val range = NumberSetting("Range", "Bed search and break range", 4.5, 1.0, 6.0, 0.1)
    val teamCheck = BooleanSetting("TeamCheck", "Protect the bed color inferred from your dyed helmet", true)
    val teamLog = BooleanSetting("TeamLog", "Show own-bed identification and mining targets in client chat", true)
    val throughWalls = BooleanSetting("ThroughWalls", "Break through covered bed faces", false)
    val rotate = BooleanSetting("Rotate", "Turn toward the current block target", true)
    val rotationSpeed = NumberSetting("RotationSpeed", "Rotation speed in degrees per tick", 180.0, 1.0, 180.0, 1.0, { rotate.value })
    val swing = BooleanSetting("Swing", "Swing while breaking", true)
    val esp = BooleanSetting("ESP", "Highlight the current block target", false)
    val progressBar = BooleanSetting("Progress Bar", "Show mining progress", true)

    private val mc: Minecraft get() = Minecraft.getInstance()
    private val teamTracker = BedTeamTracker("BedBreaker", BedTeamTracker.Action.BREAK)
    private var teamLevel: Any? = null
    private var teamPlayer: Any? = null

    private var currentTarget: BlockPos? = null
    private var currentFace: Direction? = null
    private var startedBreaking = false
    private var trackedBedPos: BlockPos? = null
    private var trackedBedFace: Direction? = null
    private var currentRotation: Rotation? = null

    private var progressValue = 0f
    private var progressTarget = 0f
    private var lastProgressTime = System.currentTimeMillis()

    init {
        listen(Render2DEvent::class.java) { onRender2D() }
    }

    override val suffix: String?
        get() = if (!teamCheck.value) null else if (teamTracker.ready) {
            "Team ${teamTracker.protectedColor!!.name.replace('_', ' ')}"
        } else {
            "Team pending"
        }

    override fun onEnable() {
        resetBreaking()
        resetTeamProtection()
    }

    override fun onDisable() {
        resetBreaking()
        resetTeamProtection()
    }

    fun onWorldChange() {
        resetBreaking()
        resetTeamProtection()
        progressTarget = 0f
        progressValue = 0f
    }

    fun onTick() {
        if (!enabled) return

        val player = mc.player
        val level = mc.level
        val gameMode = mc.gameMode
        if (player == null || level == null || gameMode == null) {
            resetBreaking()
            resetTeamProtection()
            return
        }
        if (level !== teamLevel || player !== teamPlayer) {
            resetBreaking()
            resetTeamProtection()
            teamLevel = level
            teamPlayer = player
        }
        if (!player.isAlive || player.isSpectator) {
            resetBreaking()
            resetTeamProtection()
            return
        }
        teamTracker.observeHelmet(player)
        teamTracker.logNearbyBeds(player, level, range.value, teamCheck.value, teamLog.value)

        if (shouldPauseBreaking()) {
            // Keep the source module's behavior: relinquish our target immediately when a manual
            // attack, Scaffold, or an active KillAura target takes over.
            clearBreakingState()
            return
        }
        if (teamCheck.value && !teamTracker.ready) {
            resetBreaking()
            return
        }

        val maxRange = range.value
        val eyes = player.eyePosition
        val bedTargetNow: Boolean

        if (isCurrentBreakStillValid(eyes, maxRange)) {
            bedTargetNow = isBedBlock(currentTarget!!)
        } else {
            val nextTarget = findTarget() ?: run {
                resetBreaking()
                return
            }
            resetBreaking()
            currentTarget = nextTarget.pos
            currentFace = nextTarget.face
            trackedBedPos = nextTarget.sourceBedPos
            trackedBedFace = nextTarget.sourceBedFace
            bedTargetNow = nextTarget.bedTarget
        }

        val targetPos = currentTarget ?: return
        val targetFace = currentFace ?: return
        if (!canMineTarget(trackedBedPos, targetPos)) {
            resetBreaking()
            return
        }
        currentRotation = if (rotate.value) {
            RotationUtil.toRotation(currentAimPoint(bedTargetNow))
        } else {
            null
        }

        if (!startedBreaking) {
            teamTracker.logMiningTarget(level, trackedBedPos, targetPos, teamCheck.value, teamLog.value)
            startedBreaking = gameMode.startDestroyBlock(targetPos, targetFace)
            if (!startedBreaking) {
                resetBreaking()
                return
            }
        }
        if (!canMineTarget(trackedBedPos, targetPos)) {
            resetBreaking()
            return
        }

        gameMode.continueDestroyBlock(targetPos, targetFace)

        if (swing.value) {
            player.swing(InteractionHand.MAIN_HAND)
        }

        if (level.getBlockState(targetPos).isAir) {
            resetBreaking()
        }
    }

    fun shouldRotate(): Boolean =
        enabled && rotate.value && currentTarget != null && currentRotation != null && !shouldPauseBreaking()

    fun getRotation(): Rotation? = if (shouldRotate()) currentRotation else null

    fun getRotationSpeed(): Double = rotationSpeed.value

    fun getCurrentTarget(): BlockPos? = currentTarget

    fun isBreakingTarget(): Boolean = startedBreaking && currentTarget != null

    private fun isCurrentBreakStillValid(eyes: Vec3, maxRange: Double): Boolean {
        val target = currentTarget ?: return false
        if (currentFace == null) return false
        val level = mc.level ?: return false
        if (level.getBlockState(target).isAir) return false
        if (eyes.distanceToSqr(Vec3.atCenterOf(target)) > maxRange * maxRange + 1.0) return false
        if (!canMineTarget(trackedBedPos, target)) return false
        if (trackedBedPos != null && !isBedBlock(target) && !isBedBlock(trackedBedPos!!)) return false
        return true
    }

    private fun currentAimPoint(bedTarget: Boolean): Vec3 {
        val target = currentTarget!!
        val face = currentFace!!
        if (bedTarget) return getFaceAimPoint(target, face)
        val center = Vec3.atCenterOf(target)
        return center.add(face.stepX * 0.5, face.stepY * 0.5, face.stepZ * 0.5)
    }

    private fun findTarget(): TargetData? {
        getTrackedBedTarget()?.let { return it }

        val player = mc.player ?: return null
        val level = mc.level ?: return null
        val maxRange = range.value
        val maxRangeSq = maxRange * maxRange
        val eyes = player.eyePosition
        val playerPos = player.blockPosition()
        val radius = ceil(maxRange).toInt()
        var best: TargetData? = null

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val pos = playerPos.offset(x, y, z)
                    if (level.getBlockState(pos).block !is BedBlock) continue
                    if (isTeamBed(pos)) continue
                    if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > maxRangeSq) continue

                    val candidate = getTargetData(pos, maxRange, eyes) ?: continue
                    if (best == null || candidate.priority < best.priority ||
                        (candidate.priority == best.priority && candidate.distanceSq < best.distanceSq)
                    ) {
                        best = candidate
                    }
                }
            }
        }
        return best
    }

    private fun getTrackedBedTarget(): TargetData? {
        val bedPos = trackedBedPos ?: return null
        val bedFace = trackedBedFace ?: return null
        val level = mc.level ?: return null
        val player = mc.player ?: return null
        if (level.getBlockState(bedPos).block !is BedBlock) {
            trackedBedPos = null
            trackedBedFace = null
            return null
        }
        if (isTeamBed(bedPos)) return null

        return getBedFaceTarget(bedPos, bedFace, range.value, player.eyePosition)
            ?.takeIf { it.bedTarget }
    }

    private fun isTeamBed(pos: BlockPos): Boolean {
        if (!teamCheck.value) return false
        val bed = mc.level?.getBlockState(pos)?.block as? BedBlock ?: return false
        return teamTracker.isProtectedBed(bed.color)
    }

    private fun canMineTarget(sourceBedPos: BlockPos?, targetPos: BlockPos): Boolean {
        if (!teamCheck.value) return true
        val level = mc.level ?: return false
        val source = sourceBedPos?.let { level.getBlockState(it).block } as? BedBlock ?: return false
        val targetBed = level.getBlockState(targetPos).block as? BedBlock
        return teamTracker.canMine(source.color, targetBed?.color)
    }

    private fun resetTeamProtection() {
        teamTracker.reset()
        teamLevel = null
        teamPlayer = null
    }

    private fun getTargetData(bedPos: BlockPos, maxRange: Double, eyes: Vec3): TargetData? {
        if (!canMineTarget(bedPos, bedPos)) return null
        getDirectBedTarget(bedPos, maxRange, eyes)?.let { return it }

        var coveredFaceTarget: TargetData? = null
        for (bedFace in searchDirections) {
            val candidate = getBedFaceTarget(bedPos, bedFace, maxRange, eyes) ?: continue
            if (coveredFaceTarget == null || candidate.distanceSq < coveredFaceTarget.distanceSq) {
                coveredFaceTarget = candidate
            }
        }
        return coveredFaceTarget
    }

    private fun getDirectBedTarget(bedPos: BlockPos, maxRange: Double, eyes: Vec3): TargetData? {
        val level = mc.level ?: return null
        var best: TargetData? = null
        for (face in directBreakFaces) {
            if (!isBedFaceOpen(level.getBlockState(bedPos.relative(face)))) continue
            val aimPoint = getFaceAimPoint(bedPos, face)
            val distanceSq = eyes.distanceToSqr(aimPoint)
            if (distanceSq > maxRange * maxRange) continue
            val candidate = TargetData(
                pos = bedPos,
                face = face,
                rotation = RotationUtil.toRotation(aimPoint),
                distanceSq = distanceSq,
                priority = 0,
                bedTarget = true,
                sourceBedPos = bedPos,
                sourceBedFace = face
            )
            if (best == null || candidate.distanceSq < best.distanceSq) best = candidate
        }
        return best
    }

    private fun getBedFaceTarget(bedPos: BlockPos, bedFace: Direction, maxRange: Double, eyes: Vec3): TargetData? {
        val level = mc.level ?: return null
        val adjacentPos = bedPos.relative(bedFace)
        val adjacentState = level.getBlockState(adjacentPos)
        if (isBedFaceOpen(adjacentState)) return null
        // A bed's other half (or a neighboring team's bed) is never a covering block.
        if (adjacentState.block is BedBlock || !canMineTarget(bedPos, adjacentPos)) return null

        // The source has identical target construction for ThroughWalls on and off. Keep that
        // behavior so the setting and face prioritization stay compatible with its module.
        val face = getClosestFace(adjacentPos, eyes)
        val blockerRotation = RotationUtil.getRotations(adjacentPos, face)?.let { Rotation(it[0], it[1]) }
        return TargetData(
            pos = adjacentPos,
            face = face,
            rotation = blockerRotation,
            distanceSq = eyes.distanceToSqr(Vec3.atCenterOf(adjacentPos)),
            priority = 1,
            bedTarget = false,
            sourceBedPos = bedPos,
            sourceBedFace = bedFace
        )
    }

    private fun isBedFaceOpen(state: BlockState): Boolean = state.isAir

    private fun getFaceAimPoint(bedPos: BlockPos, bedFace: Direction): Vec3 {
        val center = Vec3.atCenterOf(bedPos)
        if (bedFace == Direction.UP) {
            return Vec3(center.x, bedPos.y + 0.5625, center.z)
        }
        return center.add(bedFace.stepX * 0.42, -0.10, bedFace.stepZ * 0.42)
    }

    private fun isBedBlock(pos: BlockPos): Boolean =
        mc.level?.getBlockState(pos)?.block is BedBlock

    private fun getClosestFace(pos: BlockPos, eyes: Vec3): Direction {
        val center = Vec3.atCenterOf(pos)
        var bestFace = Direction.UP
        var bestDistance = Double.MAX_VALUE
        for (direction in searchDirections) {
            val facePoint = center.add(
                direction.stepX * 0.5,
                direction.stepY * 0.5,
                direction.stepZ * 0.5
            )
            val distance = eyes.distanceToSqr(facePoint)
            if (distance < bestDistance) {
                bestDistance = distance
                bestFace = direction
            }
        }
        return bestFace
    }

    private fun shouldPauseBreaking(): Boolean {
        if (mc.options.keyAttack.isDown) return true
        if (Scaffold.enabled) return true
        return KillAura.enabled && KillAura.targets.isNotEmpty()
    }

    private fun resetBreaking() {
        if (startedBreaking) mc.gameMode?.stopDestroyBlock()
        clearBreakingState()
    }

    private fun clearBreakingState() {
        currentTarget = null
        currentFace = null
        startedBreaking = false
        trackedBedPos = null
        trackedBedFace = null
        currentRotation = null
    }

    private fun onRender2D() {
        if (progressBar.value) renderProgressBar()
        if (enabled && esp.value) renderTargetEsp()
    }

    private fun renderProgressBar() {
        val accessor = mc.gameMode as? MultiPlayerGameModeAccessor
        val breakingBed = startedBreaking && currentTarget?.let(::isBedBlock) == true
        progressTarget = if (startedBreaking && currentTarget != null && accessor != null) {
            Mth.clamp(accessor.nocturneGetDestroyProgress() * 100.0f, 0.0f, 100.0f)
        } else {
            0f
        }
        updateProgressAnimation()
        if (progressValue < 0.5f && !startedBreaking) return

        val width = 130f
        val height = 6f
        val x = ResolutionHelper.scaledWidth * 0.5f - width * 0.5f
        val y = ResolutionHelper.scaledHeight * 0.5f + 34f
        val radius = height * 0.5f
        val leftLabel = if (breakingBed) "Bed" else "Blocker"
        val rightLabel = "${progressValue.roundToInt()}%"
        val textY = y - 8f

        NanoVGRenderer.drawString(leftLabel, x, textY, NanoVGColor(0.86f, 0.86f, 0.86f, 0.86f), 8.5f)
        NanoVGRenderer.drawRightString(rightLabel, x + width, textY, NanoVGColor(0.86f, 0.86f, 0.86f, 0.86f), 8.5f)
        NanoVGRenderer.drawShadow(x, y, width, height, radius, 7f, NanoVGColor(0f, 0f, 0f, 0.42f))
        NanoVGRenderer.drawRoundedRect(x, y, width, height, radius, NanoVGColor(0f, 0f, 0f, 0.52f))

        val fillWidth = (width * max(0.05f, progressValue / 100f)).coerceIn(0f, width)
        NanoVGRenderer.withScissor(x, y, fillWidth, height) {
            NanoVGRenderer.drawGradientRoundedRect(
                x, y, width, height, radius,
                Theme.getClientColor(0), Theme.getClientColor(6), true
            )
        }
    }

    private fun updateProgressAnimation() {
        val now = System.currentTimeMillis()
        val deltaMs = (now - lastProgressTime).coerceIn(0L, 100L).toFloat()
        lastProgressTime = now
        val distance = progressTarget - progressValue
        val speed = max(10f, kotlin.math.abs(distance) * 40f) * 0.2f
        val step = deltaMs * speed / 1000f
        progressValue = when {
            kotlin.math.abs(distance) <= step -> progressTarget
            distance > 0f -> progressValue + step
            else -> progressValue - step
        }
    }

    private fun renderTargetEsp() {
        val target = currentTarget ?: return
        if (mc.player == null) return
        val box = AABB(target)
        val corners = arrayOf(
            Vec3(box.minX, box.minY, box.minZ), Vec3(box.minX, box.minY, box.maxZ),
            Vec3(box.minX, box.maxY, box.minZ), Vec3(box.minX, box.maxY, box.maxZ),
            Vec3(box.maxX, box.minY, box.minZ), Vec3(box.maxX, box.minY, box.maxZ),
            Vec3(box.maxX, box.maxY, box.minZ), Vec3(box.maxX, box.maxY, box.maxZ)
        )
        val projected = corners.map { Render3DUtil.worldToScreen(it.x, it.y, it.z) ?: return }
        val left = projected.minOf { it.x }.coerceIn(0f, ResolutionHelper.scaledWidth)
        val top = projected.minOf { it.y }.coerceIn(0f, ResolutionHelper.scaledHeight)
        val right = projected.maxOf { it.x }.coerceIn(0f, ResolutionHelper.scaledWidth)
        val bottom = projected.maxOf { it.y }.coerceIn(0f, ResolutionHelper.scaledHeight)
        if (right <= left || bottom <= top) return
        Draw2D.roundedRect(left, top, right - left, bottom - top, 1f, NanoVGColor(1f, 0.12f, 0.12f, 0.20f))
        NanoVGRenderer.drawRoundedOutline(left, top, right - left, bottom - top, 1f, NanoVGColor(1f, 0.20f, 0.16f, 0.85f), 1f)
    }

    private data class TargetData(
        val pos: BlockPos,
        val face: Direction,
        val rotation: Rotation?,
        val distanceSq: Double,
        val priority: Int,
        val bedTarget: Boolean,
        val sourceBedPos: BlockPos,
        val sourceBedFace: Direction
    )
}
