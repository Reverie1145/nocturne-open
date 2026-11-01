package cn.reverie.module.player

import cn.reverie.mixin.client.MultiPlayerGameModeAccessor
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.module.movement.Stuck
import cn.reverie.module.world.Scaffold
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.Rotation
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.FluidTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.attribute.EnvironmentAttributes
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

object AutoMLG : Module(
    name = "AutoMLG",
    description = "Places and collects a water bucket automatically while falling",
    category = Category.PLAYER,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    val fallDistance = NumberSetting("Fall Distance", "Minimum fall distance before placing water", 2.0, 1.0, 6.0, 0.25)
    val reach = NumberSetting("Reach", "Maximum distance to the water placement target", 4.5, 2.0, 4.5, 0.1)
    val autoPickup = BooleanSetting("Auto Pickup", "Collect the placed water after entering it", true)
    val returnSlot = BooleanSetting("Return Slot", "Restore the selected hotbar slot when finished", true)
    val visualRotation = BooleanSetting("Visual Rotation", "Shows rotation in third-person view", true)
    val moveFix = ModeSetting("MoveFix", "Movement vector alignment during MLG rotation", "Silent", listOf("None", "Silent", "Strict"))
    val movementFixMode get() = moveFix
    val autoStuck = BooleanSetting("Near-ground Stuck", "Freeze movement near the ground to maximize MLG success rate (Flags on modern anticheats)", false)
    val autoStuckMode = ModeSetting("Stuck Mode", "Stuck method used when near the ground", "CancelMove", listOf("CancelMove", "NoPacket", "Ticks", "Expo Normal", "Expo Pulse"), { autoStuck.value })

    private enum class Phase {
        IDLE,
        PREPARING,
        PLACE_PENDING,
        WAITING_FOR_WATER,
        PICKUP_PENDING,
        FINISHING
    }

    private data class Target(val blockPos: BlockPos, val hitLocation: Vec3, val distance: Double)

    private var phase = Phase.IDLE
    private var previousSlot = -1
    private var bucketSlot = -1
    private var target: Target? = null
    private var actionAttempts = 0
    private var waitTicks = 0
    private var ownsStuckModule = false
    private var previousStuckMode: String? = null
    private var placedWaterPos: BlockPos? = null

    override val suffix: String
        get() = if (autoPickup.value) "Water + Pickup" else "Water"

    override fun onEnable() {
        resetState()
    }

    override fun onDisable() {
        releaseStuck()
        val player = try { Minecraft.getInstance()?.player } catch (_: Throwable) { null }
        finish(player)
    }

    @JvmStatic
    fun shouldRotate(): Boolean {
        return enabled && visualRotation.value && (phase == Phase.PREPARING || phase == Phase.PLACE_PENDING || phase == Phase.PICKUP_PENDING) && target != null
    }

    @JvmStatic
    fun getRotation(): Rotation? {
        if (!shouldRotate()) return null
        val aimLocation = when (phase) {
            Phase.PICKUP_PENDING -> {
                val pPos = placedWaterPos ?: target?.blockPos?.above() ?: return null
                Vec3(pPos.x + 0.5, pPos.y + 0.5, pPos.z + 0.5)
            }
            Phase.PREPARING, Phase.PLACE_PENDING -> {
                target?.hitLocation ?: return null
            }
            else -> return null
        }
        val rots = RotationUtil.getRotations(aimLocation) ?: return null
        return Rotation(rots[0], rots[1])
    }

    fun getMoveFix(): MoveFix = when {
        moveFix.isMode("Silent") -> MoveFix.Silent
        moveFix.isMode("Strict") -> MoveFix.Strict
        else -> MoveFix.None
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val level = mc.level
        if (level == null || mc.gameMode == null || player.isSpectator || player.isCreative || !player.isAlive || player.isPassenger) {
            finish(player)
            return
        }
        if (player.isFallFlying || player.abilities.flying || player.hasEffect(MobEffects.SLOW_FALLING)) {
            finish(player)
            return
        }
        if (Scaffold.enabled) {
            if (phase != Phase.IDLE) finish(player)
            return
        }

        if (phase == Phase.FINISHING) {
            finish(player)
            return
        }

        when (phase) {
            Phase.PREPARING -> {
                if (player.onGround() && !player.isInWater) {
                    finish(player)
                    return
                }
                if (player.deltaMovement.y >= -0.01) {
                    finish(player)
                    return
                }
                if (bucketSlot !in 0..8 || player.inventory.getItem(bucketSlot).item != Items.WATER_BUCKET) {
                    val newWaterSlot = (0 until 9).firstOrNull { player.inventory.getItem(it).item == Items.WATER_BUCKET }
                    if (newWaterSlot == null) {
                        finish(player)
                        return
                    }
                    bucketSlot = newWaterSlot
                    player.inventory.selectedSlot = newWaterSlot
                    syncCarriedItem()
                }

                val currentTarget = findBestPlacementTarget(player, maxSearchDepth = 16)
                if (currentTarget == null) {
                    finish(player)
                    return
                }
                target = currentTarget

                val maxReach = min(reach.value, player.blockInteractionRange())
                val willHitThisOrNextTick = currentTarget.distance <= maxReach || (player.y - currentTarget.hitLocation.y) <= (maxReach - 0.3)
                if (willHitThisOrNextTick) {
                    phase = Phase.PLACE_PENDING
                    actionAttempts = 0
                }
                updateAutoStuck(player)
                return
            }

            Phase.PLACE_PENDING -> {
                if (player.onGround() && !player.isInWater) {
                    finish(player)
                    return
                }
                if (player.deltaMovement.y >= -0.01) {
                    finish(player)
                    return
                }
                if (bucketSlot !in 0..8 || player.inventory.getItem(bucketSlot).item != Items.WATER_BUCKET) {
                    val newWaterSlot = (0 until 9).firstOrNull { player.inventory.getItem(it).item == Items.WATER_BUCKET }
                    if (newWaterSlot == null) {
                        finish(player)
                        return
                    }
                    bucketSlot = newWaterSlot
                    player.inventory.selectedSlot = newWaterSlot
                    syncCarriedItem()
                }

                val currentTarget = findBestPlacementTarget(player, maxSearchDepth = 8)
                if (currentTarget != null) {
                    target = currentTarget
                }
                updateAutoStuck(player)
                return
            }

            Phase.WAITING_FOR_WATER -> {
                releaseStuck()
                waitTicks++
                if (waitTicks >= 30) {
                    finish(player)
                    return
                }
                val hasDamped = player.isInWater || (player.onGround() && waitTicks >= 2)
                if (hasDamped) {
                    if (autoPickup.value) {
                        val pos = placedWaterPos ?: target?.blockPos?.above()
                        val isWater = pos != null && (level.getFluidState(pos).`is`(FluidTags.WATER) || level.getBlockState(pos).`is`(Blocks.WATER))
                        val isFeetWater = level.getFluidState(player.blockPosition()).`is`(FluidTags.WATER) || level.getBlockState(player.blockPosition()).`is`(Blocks.WATER)
                        if (isWater || isFeetWater) {
                            phase = Phase.PICKUP_PENDING
                            actionAttempts = 0
                        } else {
                            phase = Phase.FINISHING
                        }
                    } else {
                        phase = Phase.FINISHING
                    }
                }
                return
            }

            Phase.PICKUP_PENDING -> {
                releaseStuck()
                if (bucketSlot !in 0..8 || player.inventory.getItem(bucketSlot).item != Items.BUCKET) {
                    phase = Phase.FINISHING
                    return
                }
                val pos = placedWaterPos ?: target?.blockPos?.above()
                val isWater = pos != null && (level.getFluidState(pos).`is`(FluidTags.WATER) || level.getBlockState(pos).`is`(Blocks.WATER))
                val isFeetWater = level.getFluidState(player.blockPosition()).`is`(FluidTags.WATER) || level.getBlockState(player.blockPosition()).`is`(Blocks.WATER)
                if (!isWater && !isFeetWater) {
                    phase = Phase.FINISHING
                }
                return
            }

            Phase.IDLE, Phase.FINISHING -> releaseStuck()
        }

        if (player.onGround() || player.deltaMovement.y >= -0.05 ||
            player.fallDistance < fallDistance.value ||
            player.isInWater || player.isInPowderSnow
        ) return

        val waterSlot = (0 until 9).firstOrNull { player.inventory.getItem(it).item == Items.WATER_BUCKET } ?: return
        val placement = findBestPlacementTarget(player, maxSearchDepth = 16) ?: return

        previousSlot = player.inventory.selectedSlot
        bucketSlot = waterSlot
        target = placement
        actionAttempts = 0
        waitTicks = 0
        player.inventory.selectedSlot = waterSlot
        syncCarriedItem()

        val maxReach = min(reach.value, player.blockInteractionRange())
        phase = if (placement.distance <= maxReach) Phase.PLACE_PENDING else Phase.PREPARING
        updateAutoStuck(player)
    }

    /** Called immediately before the outgoing movement packet so its look rotation reaches the server first. */
    @JvmStatic
    fun onPreMotion(player: LocalPlayer) {
        if (!enabled || Scaffold.enabled) return
        if (phase != Phase.PREPARING && phase != Phase.PLACE_PENDING && phase != Phase.PICKUP_PENDING) return
        val aimLocation = when (phase) {
            Phase.PICKUP_PENDING -> {
                val pPos = placedWaterPos ?: target?.blockPos?.above() ?: return
                Vec3(pPos.x + 0.5, pPos.y + 0.5, pPos.z + 0.5)
            }
            Phase.PREPARING, Phase.PLACE_PENDING -> {
                target?.hitLocation ?: return
            }
            else -> return
        }
        val rotations = RotationUtil.getRotations(aimLocation) ?: return
        RotationManager.setView(rotations, getMoveFix())
    }

    /** Used by the LocalPlayer mixin to check whether to place water right before the movement packet. */
    @JvmStatic
    fun shouldPlace(player: LocalPlayer): Boolean {
        if (!enabled || Scaffold.enabled) return false
        if (phase != Phase.PLACE_PENDING && phase != Phase.PICKUP_PENDING) return false
        return makeHit(player) != null && Minecraft.getInstance().gameMode != null
    }

    /** Called before the outgoing movement packet so interaction rotation and motion packet rotation match exactly. */
    @JvmStatic
    fun placeBeforePacket(player: LocalPlayer, yaw: Float, pitch: Float) {
        if (!shouldPlace(player)) return
        val mc = Minecraft.getInstance()
        val gameMode = mc.gameMode ?: return

        val originalYaw = player.yRot
        val originalPitch = player.xRot
        player.yRot = yaw
        player.xRot = pitch
        val result = try {
            gameMode.useItem(player, InteractionHand.MAIN_HAND)
        } finally {
            player.yRot = originalYaw
            player.xRot = originalPitch
        }

        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND)
            actionAttempts = 0
            releaseStuck()
            if (phase == Phase.PLACE_PENDING) {
                placedWaterPos = target?.blockPos?.above()
                phase = Phase.WAITING_FOR_WATER
                waitTicks = 0
            } else {
                phase = Phase.FINISHING
            }
        } else {
            actionAttempts++
            if (actionAttempts >= 8) {
                phase = Phase.FINISHING
            }
        }
    }

    @JvmStatic
    fun placeAfterFlying(player: LocalPlayer) {}

    private fun updateAutoStuck(player: LocalPlayer) {
        if (!autoStuck.value || (phase != Phase.PLACE_PENDING && phase != Phase.PREPARING) || target == null || player.onGround()) {
            releaseStuck()
            return
        }
        val targetPos = target?.hitLocation ?: return
        val dist = player.eyePosition.distanceTo(targetPos)
        if (dist <= reach.value + 0.8) {
            if (!Stuck.enabled) {
                previousStuckMode = Stuck.mode.value
                Stuck.mode.value = autoStuckMode.value
                Stuck.enabled = true
                ownsStuckModule = true
            }
        } else {
            releaseStuck()
        }
    }

    private fun releaseStuck() {
        if (ownsStuckModule) {
            if (Stuck.enabled) Stuck.enabled = false
            previousStuckMode?.let { Stuck.mode.value = it }
            previousStuckMode = null
            ownsStuckModule = false
        }
    }

    private fun findBestPlacementTarget(player: LocalPlayer, maxSearchDepth: Int = 16): Target? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        val eye = player.eyePosition

        val columns = ArrayList<Pair<Int, Int>>(12)
        fun addCol(cx: Double, cz: Double) {
            val pair = Pair(floor(cx).toInt(), floor(cz).toInt())
            if (!columns.contains(pair)) columns.add(pair)
        }

        addCol(player.x, player.z)
        val mx = player.deltaMovement.x
        val mz = player.deltaMovement.z
        if (abs(mx) > 0.01 || abs(mz) > 0.01) {
            addCol(player.x + mx, player.z + mz)
            addCol(player.x + mx * 2.0, player.z + mz * 2.0)
            addCol(player.x + mx * 3.0, player.z + mz * 3.0)
            addCol(player.x + mx * 4.0, player.z + mz * 4.0)
        }

        val bb = player.boundingBox
        addCol(bb.minX, bb.minZ)
        addCol(bb.maxX, bb.minZ)
        addCol(bb.minX, bb.maxZ)
        addCol(bb.maxX, bb.maxZ)

        val firstY = floor(player.y).toInt()
        val maxReach = min(reach.value, player.blockInteractionRange())

        var bestTarget: Target? = null
        var bestReachableTarget: Target? = null
        var bestReachableDist = Double.MAX_VALUE
        var highestSurfaceY = Double.NEGATIVE_INFINITY

        for ((cx, cz) in columns) {
            for (y in firstY downTo firstY - maxSearchDepth) {
                val supportPos = BlockPos(cx, y, cz)
                if (!level.hasChunkAt(supportPos) || !level.worldBorder.isWithinBounds(supportPos)) continue
                val support = level.getBlockState(supportPos)
                val collision = support.getCollisionShape(level, supportPos)
                if (collision.isEmpty || support.liquid() || support.canBeReplaced()) continue

                val waterPos = supportPos.above()
                val waterState = level.getBlockState(waterPos)
                if (!waterState.canBeReplaced() || !level.getFluidState(waterPos).isEmpty) continue

                try {
                    if (level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, waterPos) == true) {
                        continue
                    }
                } catch (_: Throwable) {
                }

                val topOffset = try {
                    if (!collision.isEmpty) collision.max(Direction.Axis.Y) else 1.0
                } catch (_: Throwable) { 1.0 }

                val surfaceY = y + topOffset
                val clampedX = player.x.coerceIn(cx + 0.15, cx + 0.85)
                val clampedZ = player.z.coerceIn(cz + 0.15, cz + 0.85)
                val hitLocation = Vec3(clampedX, surfaceY, clampedZ)
                val dist = eye.distanceTo(hitLocation)

                val targetCandidate = Target(supportPos, hitLocation, dist)

                if (dist <= maxReach) {
                    if (bestReachableTarget == null || surfaceY > bestReachableTarget.hitLocation.y || (surfaceY == bestReachableTarget.hitLocation.y && dist < bestReachableDist)) {
                        bestReachableTarget = targetCandidate
                        bestReachableDist = dist
                    }
                }

                if (surfaceY > highestSurfaceY) {
                    highestSurfaceY = surfaceY
                    bestTarget = targetCandidate
                }
                break
            }
        }

        return bestReachableTarget ?: bestTarget
    }

    private fun makeHit(player: LocalPlayer): BlockHitResult? {
        val maxReach = min(reach.value, player.blockInteractionRange())
        val reachSquared = maxReach * maxReach

        when (phase) {
            Phase.PREPARING, Phase.PLACE_PENDING -> {
                val current = target ?: return null
                if (bucketSlot !in 0..8 || player.inventory.getItem(bucketSlot).item != Items.WATER_BUCKET) return null
                val eye = player.eyePosition
                if (eye.distanceToSqr(current.hitLocation) > reachSquared) return null
                return BlockHitResult(current.hitLocation, Direction.UP, current.blockPos, false)
            }
            Phase.PICKUP_PENDING -> {
                if (bucketSlot !in 0..8 || player.inventory.getItem(bucketSlot).item != Items.BUCKET) return null
                val level = Minecraft.getInstance().level ?: return null
                val pos = placedWaterPos ?: target?.blockPos?.above() ?: return null
                val isWater = level.getFluidState(pos).`is`(FluidTags.WATER) || level.getBlockState(pos).`is`(Blocks.WATER)
                val targetPos = if (isWater) {
                    pos
                } else {
                    val feetPos = player.blockPosition()
                    val feetWater = level.getFluidState(feetPos).`is`(FluidTags.WATER) || level.getBlockState(feetPos).`is`(Blocks.WATER)
                    if (feetWater) feetPos else return null
                }
                val hitLoc = Vec3(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5)
                if (player.eyePosition.distanceToSqr(hitLoc) > reachSquared) return null
                return BlockHitResult(hitLoc, Direction.UP, targetPos, false)
            }
            else -> return null
        }
    }

    private fun syncCarriedItem() {
        val mc = try { Minecraft.getInstance() } catch (_: Throwable) { null } ?: return
        val gameMode = mc.gameMode as? MultiPlayerGameModeAccessor ?: return
        gameMode.nocturneEnsureHasSentCarriedItem()
    }

    private fun finish(player: LocalPlayer?) {
        releaseStuck()
        RotationManager.clearRotationTarget()
        if (player != null && returnSlot.value && previousSlot in 0..8 && player.inventory.selectedSlot == bucketSlot) {
            player.inventory.selectedSlot = previousSlot
            syncCarriedItem()
        }
        resetState()
    }

    private fun resetState() {
        phase = Phase.IDLE
        previousSlot = -1
        bucketSlot = -1
        target = null
        actionAttempts = 0
        waitTicks = 0
        placedWaterPos = null
    }
}
