package cn.reverie.module.player

import cn.reverie.app.event.render.Render2DEvent
import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.StringSetting
import cn.reverie.module.combat.KillAura
import cn.reverie.module.player.beddefender.BedDefenseBlocks
import cn.reverie.module.player.beddefender.BedDefenseLayout
import cn.reverie.module.player.beddefender.BedDefenseRaycast
import cn.reverie.module.player.bedteam.BedTeamTracker
import cn.reverie.module.world.Scaffold
import cn.reverie.render.core.ResolutionHelper
import cn.reverie.render.draw.Draw2D
import cn.reverie.render.nanovg.NanoVGColor
import cn.reverie.util.render.Render3DUtil
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.properties.BedPart
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object BedDefender : Module(
    name = "BedDefender",
    description = "Automatically builds a protective pyramid around your bed",
    category = Category.PLAYER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    val bedRange = NumberSetting("Bed Range", "Maximum range used to find your bed", 16.0, 4.0, 32.0, 1.0)
    val teamCheck = BooleanSetting("Team Check", "Defend only the bed color inferred from your dyed helmet", true)
    val teamLog = BooleanSetting("TeamLog", "Show own-bed identification and defense targets in client chat", true)
    val blockMode = ModeSetting("Block", "Block type to use for the defense", "Any", listOf("Any", "Obsidian", "End Stone", "Wool", "Planks", "Custom"))
    val customBlock = StringSetting("Custom Block", "Choose a full block by registry ID or from your hotbar", "minecraft:obsidian", { blockMode.isMode("Custom") })
    val height = NumberSetting("Height", "Number of stepped pyramid layers around the bed", 1.0, 1.0, 3.0, 1.0)
    val topCover = BooleanSetting("Top Cover", "Fill the column above the bed to close the pyramid", true)
    val rotate = BooleanSetting("Rotate", "Turn toward each placement position", true)
    val rotationSpeed = NumberSetting("Rotation Speed", "Maximum turn speed in degrees per tick", 120.0, 1.0, 180.0, 1.0, { rotate.value })
    val moveFix = ModeSetting("MoveFix", "Movement correction while turning", "Silent", listOf("None", "Silent", "Strict"), { rotate.value })
    val rayCast = ModeSetting("RayCast", "Choose how rotations and block hits are checked before placing", "Face", BedDefenseRaycast.modes)
    val delay = NumberSetting("Delay", "Minimum time between block placements in milliseconds", 100.0, 50.0, 1000.0, 25.0)
    val swing = BooleanSetting("Swing", "Swing after placing a defense block", true)
    val render = BooleanSetting("Render", "Show remaining defense positions and the next target", true)

    private val mc: Minecraft get() = Minecraft.getInstance()
    private val teamTracker = BedTeamTracker("BedDefender", BedTeamTracker.Action.DEFEND)

    private var world: Any? = null
    private var worldPlayer: LocalPlayer? = null
    private var bedPos: BlockPos? = null
    private var lastBedScanTick = Int.MIN_VALUE
    private var plannedPositions: List<BlockPos> = emptyList()
    private var target: PlacementTarget? = null
    private var targetRotation: Rotation? = null
    private var lastMotion: BedDefenseRaycast.Motion? = null
    private var nextPlaceAt = 0L
    private val retryAfter = HashMap<BlockPos, Long>()
    private var cachedCustomId: String? = null
    private var cachedCustomBlock: Block? = null
    private var shoulderBed: BlockPos? = null
    private var roofShoulder = BedDefenseLayout.roofShoulders.first()

    private var managedSlot = -1
    private var restoreSlot = -1
    private var userChangedSlot = false
    private var pendingSlotRestore: SlotRestore? = null

    init {
        listen(Render2DEvent::class.java) { renderPositions() }
    }

    override val suffix: String
        get() {
            val defense = "${materialName()} ${height.getInt()}L"
            if (!teamCheck.value) return defense
            val team = if (teamTracker.ready) teamTracker.protectedColor!!.name.replace('_', ' ') else "pending"
            return "$defense | Team $team"
        }

    fun materialName(): String = if (blockMode.isMode("Custom")) resolveCustomBlock()?.let(BedDefenseBlocks::displayName) ?: "Custom" else blockMode.value

    fun resolveCustomBlock(): Block? {
        if (cachedCustomId != customBlock.value) {
            cachedCustomId = customBlock.value
            cachedCustomBlock = BedDefenseBlocks.find(customBlock.value)
        }
        return cachedCustomBlock
    }

    fun selectCustomBlock(block: Block): Boolean {
        if (!BedDefenseBlocks.canUse(block)) return false
        customBlock.value = BedDefenseBlocks.id(block)
        blockMode.value = "Custom"
        clearTarget()
        return true
    }

    fun previewMaterial(): String = if (blockMode.isMode("Custom")) customBlock.value else blockMode.value

    fun previewRgb(): Int? = if (blockMode.isMode("Custom")) resolveCustomBlock()?.let(BedDefenseBlocks::previewRgb) else null

    internal fun previewShoulder(): BedDefenseLayout.Cell = roofShoulder

    override fun onEnable() {
        resetState(shouldRestoreSlot = false)
        userChangedSlot = false
    }

    override fun onDisable() {
        resetState(shouldRestoreSlot = true)
        userChangedSlot = false
    }

    fun onWorldChange() {
        pendingSlotRestore = null
        resetState(shouldRestoreSlot = false)
        world = null
        userChangedSlot = false
    }

    @JvmStatic
    fun shouldRotate(): Boolean = enabled && rotate.value && target != null && targetRotation != null

    fun getRotation(): Rotation? = if (shouldRotate()) targetRotation else null

    fun getRotationSpeed(): Double = rotationSpeed.value

    fun getMoveFix(): MoveFix = when {
        moveFix.isMode("Silent") -> MoveFix.Silent
        moveFix.isMode("Strict") -> MoveFix.Strict
        else -> MoveFix.None
    }

    @JvmStatic
    fun onClientTick(player: LocalPlayer) {
        // Run before gameMode.tick and vanilla keybind actions, so its normal slot sync is first.
        applyPendingSlotRestore(player)
        val level = mc.level
        if (!enabled || level == null || mc.gameMode == null || player.isSpectator || !player.isAlive) {
            clearTarget()
            if (enabled) releaseManagedSlot(player)
            if (enabled && (level == null || player.isSpectator || !player.isAlive)) {
                resetState(shouldRestoreSlot = false)
                world = null
            }
            return
        }

        if (world !== level || worldPlayer !== player) {
            resetState(shouldRestoreSlot = false)
            world = level
            worldPlayer = player
        }

        teamTracker.observeHelmet(player)
        teamTracker.logNearbyBeds(player, level, bedRange.value, teamCheck.value, teamLog.value)
        if (teamCheck.value && !teamTracker.ready) {
            bedPos = null
            lastBedScanTick = Int.MIN_VALUE
            plannedPositions = emptyList()
            clearTarget()
            releaseManagedSlot(player)
            return
        }

        if (shouldPause(player)) {
            clearTarget()
            releaseManagedSlot(player)
            return
        }

        val bed = resolveBed(player) ?: run {
            plannedPositions = emptyList()
            clearTarget()
            releaseManagedSlot(player)
            return
        }
        teamTracker.logDefenseBed(level, bed, teamCheck.value, teamLog.value)
        if (height.getInt() == 1 && topCover.value) roofShoulder = chooseRoofShoulder(player, level, bed)
        plannedPositions = buildDefensePositions(level, bed)
        trackManualSlotChange(player)
        if (!isDefenseStack(player.mainHandItem) && !isDefenseStack(player.offhandItem) &&
            (userChangedSlot || (0..8).none { isDefenseStack(player.inventory.getItem(it)) })) {
            clearTarget()
            releaseManagedSlot(player)
            return
        }
        val now = System.currentTimeMillis()
        retryAfter.entries.removeIf { it.value <= now }
        val reference = RotationManager.lastSentRotation() ?: Rotation(player.yRot, player.xRot)
        // Keep a valid face while turning, so nearby alternatives do not make the aim oscillate every tick.
        val retained = target?.takeIf { it.destination in plannedPositions && isValidTarget(player, it, now) }
        val placement = retained ?: plannedPositions.asSequence()
            .filter { (retryAfter[it] ?: 0L) <= now && level.getBlockState(it).canBeReplaced() }
            .mapNotNull { findPlacement(player, it, reference) }
            .minWithOrNull(compareBy<PlacementTarget> { it.destination.y }.thenBy { it.score })

        if (placement == null) {
            clearTarget()
            releaseManagedSlot(player)
            return
        }

        val hand = selectDefenseHand(player)
        if (hand == null) {
            clearTarget()
            releaseManagedSlot(player)
            return
        }

        target = placement.copy(hand = hand)
        targetRotation = if (rotate.value) RotationUtil.toRotation(placement.hitLocation) else null
    }

    /** Interactions belong before flying; the following packet carries the rotation checked here. */
    @JvmStatic
    fun placeBeforeMotion(player: LocalPlayer, yaw: Float, pitch: Float): Boolean {
        if (!enabled) return false
        val placement = target ?: return false
        val level = mc.level ?: return false
        val gameMode = mc.gameMode ?: return false
        val now = System.currentTimeMillis()
        if (now < nextPlaceAt || player.isSpectator || !player.isAlive || shouldPause(player)) return false
        if (!isValidTarget(player, placement, now)) {
            clearTarget()
            return false
        }

        val heldStack = player.getItemInHand(placement.hand)
        if (!isDefenseStack(heldStack)) return false

        // useItemOn syncs the slot internally. If it changed after the early tick, wait for the next one.
        val accessor = gameMode as? MultiPlayerGameModeAccessor ?: return false
        if (accessor.nocturneGetCarriedIndex() != player.inventory.selectedSlot) return false

        val motion = BedDefenseRaycast.Motion(Rotation(yaw, pitch), player.position(), player.eyePosition)
        val hit = BedDefenseRaycast.placementHit(
            rayCast.value, player, level, placement.support, placement.face, placement.hitLocation, motion, lastMotion
        ) ?: return false
        if (hit.type != HitResult.Type.BLOCK || hit.blockPos != placement.support || hit.direction != placement.face ||
            hit.blockPos.relative(hit.direction) != placement.destination || !isSafeSupport(level, hit.blockPos)) return false

        // Every policy resolves a verified hit on a non-bed support face before interacting.
        val defenseBed = bedPos ?: return false
        teamTracker.logPlacementTarget(level, player, defenseBed, placement.destination, placement.hand, teamLog.value)
        val result = gameMode.useItemOn(player, placement.hand, hit)
        if (result.consumesAction()) {
            if (swing.value) player.swing(placement.hand)
            nextPlaceAt = now + delay.value.toLong()
            retryAfter[placement.destination] = now + max(250L, delay.value.toLong())
            clearTarget()
        } else {
            nextPlaceAt = now + max(100L, delay.value.toLong())
            retryAfter[placement.destination] = now + 500L
            clearTarget()
        }
        // PASS/FAIL still sends UseItemOn. The caller must close every attempt with the normal flying packet.
        return true
    }

    /** Record history only; this phase must not send placements or slot changes. */
    @JvmStatic
    fun afterMotion(player: LocalPlayer) {
        if (enabled) lastMotion = BedDefenseRaycast.captureMotion(player)
    }

    private fun resolveBed(player: LocalPlayer): BlockPos? {
        val level = mc.level ?: return null
        val cached = bedPos
        val cachedState = cached?.let(level::getBlockState)
        val cachedValid = cached != null && cachedState != null && cachedState.block is BedBlock &&
            cachedState.getValue(BedBlock.PART) == BedPart.FOOT &&
            player.position().distanceToSqr(Vec3.atCenterOf(cached)) <= bedRange.value * bedRange.value &&
            isTeamBed(cachedState)

        if (cachedValid) return cached
        val cacheFresh = lastBedScanTick != Int.MIN_VALUE && player.tickCount - lastBedScanTick < BED_SCAN_INTERVAL
        if (cacheFresh && cached == null) return null
        lastBedScanTick = player.tickCount
        bedPos = findNearestBed(player)
        return bedPos
    }

    private fun findNearestBed(player: LocalPlayer): BlockPos? {
        val level = mc.level ?: return null
        val radius = bedRange.getInt()
        val center = player.blockPosition()
        val radiusSq = bedRange.value * bedRange.value
        var best: BlockPos? = null
        var bestDistance = Double.MAX_VALUE

        for (x in center.x - radius..center.x + radius) {
            for (y in max(level.minY, center.y - radius)..min(level.maxY - 1, center.y + radius)) {
                for (z in center.z - radius..center.z + radius) {
                    val pos = BlockPos(x, y, z)
                    val state = level.getBlockState(pos)
                    if (state.block !is BedBlock || state.getValue(BedBlock.PART) != BedPart.FOOT) continue
                    if (!isTeamBed(state)) continue
                    val distance = player.position().distanceToSqr(Vec3.atCenterOf(pos))
                    if (distance > radiusSq || distance >= bestDistance) continue
                    best = pos.immutable()
                    bestDistance = distance
                }
            }
        }
        return best
    }

    private fun isTeamBed(state: net.minecraft.world.level.block.state.BlockState): Boolean {
        val bed = state.block as? BedBlock ?: return false
        return !teamCheck.value || teamTracker.isOwnBed(bed.color)
    }

    private fun isCurrentBedValid(player: LocalPlayer): Boolean {
        val level = mc.level ?: return false
        if (world !== level || worldPlayer !== player) return false
        val bed = bedPos ?: return false
        val state = level.getBlockState(bed)
        return state.block is BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT &&
            player.position().distanceToSqr(Vec3.atCenterOf(bed)) <= bedRange.value * bedRange.value &&
            (!teamCheck.value || teamTracker.matchesCurrentHelmet(player)) && isTeamBed(state)
    }

    private fun buildDefensePositions(level: net.minecraft.client.multiplayer.ClientLevel, bed: BlockPos): List<BlockPos> {
        val state = level.getBlockState(bed)
        if (state.block !is BedBlock || state.getValue(BedBlock.PART) != BedPart.FOOT) return emptyList()
        val facing = state.getValue(HorizontalDirectionalBlock.FACING)
        return BedDefenseLayout.cells(height.getInt(), topCover.value, roofShoulder).map { cell ->
            val offset = cell.oriented(facing.stepX, facing.stepZ)
            bed.offset(offset.x, offset.y, offset.z)
        }
    }

    private fun chooseRoofShoulder(player: LocalPlayer, level: ClientLevel, bed: BlockPos): BedDefenseLayout.Cell {
        val facing = level.getBlockState(bed).getValue(HorizontalDirectionalBlock.FACING)
        fun worldPos(cell: BedDefenseLayout.Cell): BlockPos {
            val offset = cell.oriented(facing.stepX, facing.stepZ)
            return bed.offset(offset.x, offset.y, offset.z)
        }
        // Once the shoulder exists, keep it instead of building more supports when the player moves.
        if (shoulderBed == bed && isSafeSupport(level, worldPos(roofShoulder))) return roofShoulder
        shoulderBed = bed
        var selected = roofShoulder
        var bestScore = Double.MAX_VALUE
        for (cell in BedDefenseLayout.roofShoulders) {
            val support = worldPos(cell)
            val state = level.getBlockState(support)
            if (!level.worldBorder.isWithinBounds(support) || AABB(support).intersects(player.boundingBox)) continue
            if (!state.canBeReplaced() && !isSafeSupport(level, support)) continue
            val below = support.below()
            if (!level.getBlockState(below).canBeReplaced() && !isSafeSupport(level, below)) continue
            val roof = worldPos(BedDefenseLayout.Cell(0, 1, cell.z.coerceIn(0, 1)))
            val inward = Vec3((roof.x - support.x).toDouble(), 0.0, (roof.z - support.z).toDouble())
            val point = Vec3.atCenterOf(support).add(inward.scale(0.5))
            val visibleSide = player.eyePosition.subtract(point).dot(inward) > 0.001
            val ray = level.clip(ClipContext(player.eyePosition, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
            val clearLine = ray.type == HitResult.Type.MISS || ray.blockPos == support
            val score = player.eyePosition.distanceToSqr(point) + (if (visibleSide) 0.0 else 1000.0) + (if (clearLine) 0.0 else 100.0)
            if (score < bestScore) {
                bestScore = score
                selected = cell
            }
        }
        return selected
    }

    private fun isSafeSupport(level: ClientLevel, support: BlockPos): Boolean {
        return BedDefenseBlocks.canSupport(level, support)
    }

    private fun isValidTarget(player: LocalPlayer, placement: PlacementTarget, now: Long): Boolean {
        val level = mc.level ?: return false
        return isCurrentBedValid(player) && placement.destination in plannedPositions &&
            (retryAfter[placement.destination] ?: 0L) <= now &&
            placement.destination.y in level.minY until level.maxY &&
            level.getBlockState(placement.destination).canBeReplaced() &&
            level.worldBorder.isWithinBounds(placement.destination) &&
            !AABB(placement.destination).intersects(player.boundingBox) &&
            placement.support.relative(placement.face) == placement.destination &&
            isSafeSupport(level, placement.support) && isVisibleSupport(player, placement.support, placement.face, placement.hitLocation)
    }

    private fun findPlacement(player: LocalPlayer, destination: BlockPos, reference: Rotation): PlacementTarget? {
        val level = mc.level ?: return null
        if (destination.y !in level.minY until level.maxY) return null
        if (!level.getBlockState(destination).canBeReplaced() || !level.worldBorder.isWithinBounds(destination)) return null
        if (AABB(destination).intersects(player.boundingBox)) return null

        val reach = BedDefenseRaycast.reach(rayCast.value, player)
        val reachSq = reach * reach
        var best: PlacementTarget? = null
        for (face in Direction.values()) {
            val support = destination.relative(face.opposite)
            if (!isSafeSupport(level, support)) continue
            val supportState = level.getBlockState(support)
            val shape = supportState.getCollisionShape(level, support)
            for (local in shape.toAabbs()) for (point in facePoints(support, local, face)) {
                val distance = player.eyePosition.distanceToSqr(point)
                if (distance > reachSq) continue
                val towardPlayer = player.eyePosition.subtract(point)
                if (towardPlayer.x * face.stepX + towardPlayer.y * face.stepY + towardPlayer.z * face.stepZ <= 0.001) continue
                val rotation = RotationUtil.toRotation(point)
                val turn = abs(Mth.wrapDegrees(rotation.yaw - reference.yaw)) + 0.6 * abs(rotation.pitch - reference.pitch)
                val score = turn / 180.0 + distance * 0.03 + if (face == Direction.UP) 0.0 else 0.06
                if (best != null && score >= best.score) continue
                if (!isVisibleSupport(player, support, face, point)) continue
                best = PlacementTarget(destination, support, face, point, InteractionHand.MAIN_HAND, score)
            }
        }
        return best
    }

    private fun facePoints(pos: BlockPos, local: AABB, face: Direction): Sequence<Vec3> = sequence {
        val minX = pos.x + local.minX
        val minY = pos.y + local.minY
        val minZ = pos.z + local.minZ
        val maxX = pos.x + local.maxX
        val maxY = pos.y + local.maxY
        val maxZ = pos.z + local.maxZ
        for ((u, v) in FACE_SAMPLES) {
            yield(when (face) {
                Direction.DOWN -> Vec3(Mth.lerp(u, minX, maxX), minY, Mth.lerp(v, minZ, maxZ))
                Direction.UP -> Vec3(Mth.lerp(u, minX, maxX), maxY, Mth.lerp(v, minZ, maxZ))
                Direction.NORTH -> Vec3(Mth.lerp(u, minX, maxX), Mth.lerp(v, minY, maxY), minZ)
                Direction.SOUTH -> Vec3(Mth.lerp(u, minX, maxX), Mth.lerp(v, minY, maxY), maxZ)
                Direction.WEST -> Vec3(minX, Mth.lerp(v, minY, maxY), Mth.lerp(u, minZ, maxZ))
                Direction.EAST -> Vec3(maxX, Mth.lerp(v, minY, maxY), Mth.lerp(u, minZ, maxZ))
            })
        }
    }

    private fun isVisibleSupport(player: LocalPlayer, support: BlockPos, face: Direction, point: Vec3): Boolean {
        val level = mc.level ?: return false
        return BedDefenseRaycast.visibleHit(rayCast.value, player, level, support, face, point) != null
    }

    private fun trackManualSlotChange(player: LocalPlayer) {
        if (managedSlot >= 0 && player.inventory.selectedSlot != managedSlot) {
            managedSlot = -1
            restoreSlot = -1
            userChangedSlot = true
        }
    }

    private fun selectDefenseHand(player: LocalPlayer): InteractionHand? {
        trackManualSlotChange(player)
        if (isDefenseStack(player.mainHandItem)) return InteractionHand.MAIN_HAND
        if (userChangedSlot) {
            return if (isDefenseStack(player.offhandItem)) InteractionHand.OFF_HAND else null
        }

        val bestSlot = (0..8)
            .filter { isDefenseStack(player.inventory.getItem(it)) }
            .minWithOrNull(compareBy<Int> { blockPriority((player.inventory.getItem(it).item as BlockItem).block) }
                .thenByDescending { player.inventory.getItem(it).count })
        if (bestSlot != null) {
            if (managedSlot < 0) restoreSlot = player.inventory.selectedSlot
            player.inventory.selectedSlot = bestSlot
            managedSlot = bestSlot
            return InteractionHand.MAIN_HAND
        }

        if (isDefenseStack(player.offhandItem)) {
            releaseManagedSlot(player)
            return InteractionHand.OFF_HAND
        }
        releaseManagedSlot(player)
        return null
    }

    private fun isDefenseStack(stack: net.minecraft.world.item.ItemStack): Boolean {
        val blockItem = stack.item as? BlockItem ?: return false
        val block = blockItem.block
        if (stack.isEmpty || !BedDefenseBlocks.canUse(block)) return false
        return blockPriority(block) < Int.MAX_VALUE
    }

    private fun blockPriority(block: Block): Int {
        val state = block.defaultBlockState()
        return when (blockMode.value) {
            "Custom" -> if (block === resolveCustomBlock()) 0 else Int.MAX_VALUE
            "Obsidian" -> if (block == net.minecraft.world.level.block.Blocks.OBSIDIAN || block == net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN) 0 else Int.MAX_VALUE
            "End Stone" -> if (block == net.minecraft.world.level.block.Blocks.END_STONE) 0 else Int.MAX_VALUE
            "Wool" -> if (state.`is`(BlockTags.WOOL)) 0 else Int.MAX_VALUE
            "Planks" -> if (state.`is`(BlockTags.PLANKS)) 0 else Int.MAX_VALUE
            else -> when {
                block == net.minecraft.world.level.block.Blocks.OBSIDIAN || block == net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN -> 0
                block == net.minecraft.world.level.block.Blocks.END_STONE -> 1
                state.`is`(BlockTags.WOOL) -> 2
                state.`is`(BlockTags.PLANKS) -> 3
                else -> 4
            }
        }
    }

    private fun shouldPause(player: LocalPlayer): Boolean =
        mc.gui.screen() != null || mc.options.keyAttack.isDown || mc.options.keyUse.isDown || player.isUsingItem || player.isPassenger || Scaffold.enabled ||
            (KillAura.enabled && KillAura.targets.isNotEmpty()) || BedBreaker.shouldRotate() || ChestAura.shouldRotate() || AutoMLG.shouldRotate()

    private fun releaseManagedSlot(player: LocalPlayer) {
        if (managedSlot >= 0 && player.inventory.selectedSlot == managedSlot && restoreSlot in 0..8) {
            // Disabling, finishing, and pausing can follow a right click in this tick. Restore next tick instead.
            pendingSlotRestore = SlotRestore(player, mc.level, managedSlot, restoreSlot)
        }
        managedSlot = -1
        restoreSlot = -1
    }

    private fun applyPendingSlotRestore(player: LocalPlayer) {
        val pending = pendingSlotRestore ?: return
        pendingSlotRestore = null
        if (pending.player === player && pending.level === mc.level && player.inventory.selectedSlot == pending.fromSlot) {
            player.inventory.selectedSlot = pending.toSlot
        }
    }

    private fun clearTarget() {
        target = null
        targetRotation = null
    }

    private fun resetState(shouldRestoreSlot: Boolean) {
        if (shouldRestoreSlot) mc.player?.let(::releaseManagedSlot) else {
            managedSlot = -1
            restoreSlot = -1
        }
        bedPos = null
        teamTracker.reset()
        worldPlayer = null
        lastBedScanTick = Int.MIN_VALUE
        plannedPositions = emptyList()
        clearTarget()
        lastMotion = null
        nextPlaceAt = 0L
        retryAfter.clear()
        shoulderBed = null
        roofShoulder = BedDefenseLayout.roofShoulders.first()
    }

    private fun renderPositions() {
        if (!enabled || !render.value || plannedPositions.isEmpty()) return
        val level = mc.level ?: return
        if (mc.player == null) return
        val screenWidth = ResolutionHelper.scaledWidth
        val screenHeight = ResolutionHelper.scaledHeight
        if (screenWidth <= 0f || screenHeight <= 0f) return

        for (pos in plannedPositions) {
            if (!level.getBlockState(pos).canBeReplaced()) continue
            val box = AABB(pos)
            val corners = arrayOf(
                Vec3(box.minX, box.minY, box.minZ), Vec3(box.minX, box.minY, box.maxZ),
                Vec3(box.minX, box.maxY, box.minZ), Vec3(box.minX, box.maxY, box.maxZ),
                Vec3(box.maxX, box.minY, box.minZ), Vec3(box.maxX, box.minY, box.maxZ),
                Vec3(box.maxX, box.maxY, box.minZ), Vec3(box.maxX, box.maxY, box.maxZ)
            )
            val projected = corners.map { Render3DUtil.worldToScreen(it.x, it.y, it.z) ?: return@map null }
            if (projected.any { it == null }) continue
            val points = projected.filterNotNull()
            val left = points.minOf { it.x }.coerceIn(0f, screenWidth)
            val top = points.minOf { it.y }.coerceIn(0f, screenHeight)
            val right = points.maxOf { it.x }.coerceIn(0f, screenWidth)
            val bottom = points.maxOf { it.y }.coerceIn(0f, screenHeight)
            if (right <= left || bottom <= top) continue

            val isNext = target?.destination == pos
            val fill = if (isNext) NanoVGColor(1.0f, 0.64f, 0.16f, 0.24f) else NanoVGColor(0.25f, 0.78f, 0.58f, 0.12f)
            val outline = if (isNext) NanoVGColor(1.0f, 0.71f, 0.24f, 0.96f) else NanoVGColor(0.32f, 0.90f, 0.70f, 0.70f)
            Draw2D.roundedRect(left, top, right - left, bottom - top, 1.5f, fill)
            Draw2D.roundedOutline(left, top, right - left, bottom - top, 1.5f, outline, if (isNext) 1.6f else 1.0f)
        }
    }

    private data class SlotRestore(val player: LocalPlayer, val level: ClientLevel?, val fromSlot: Int, val toSlot: Int)

    private data class PlacementTarget(
        val destination: BlockPos,
        val support: BlockPos,
        val face: Direction,
        val hitLocation: Vec3,
        val hand: InteractionHand,
        val score: Double
    )

    private const val BED_SCAN_INTERVAL = 10
    private val FACE_SAMPLES = listOf(
        0.5 to 0.5, 0.28 to 0.5, 0.72 to 0.5, 0.5 to 0.28, 0.5 to 0.72,
        0.28 to 0.28, 0.28 to 0.72, 0.72 to 0.28, 0.72 to 0.72
    )
}
