package cn.reverie.module.world.scaffold

import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.world.Scaffold
import cn.reverie.util.player.MovementUtil
import cn.reverie.util.rotation.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

internal fun Scaffold.onMoveInputImpl(player: LocalPlayer, event: MoveInputEvent) {
    if (!enabled) return
    val mc = Minecraft.getInstance()

    if (isOmniSprintEnabled() && !fast.value) {
        omniRawForward = event.forward
        omniRawStrafe = event.strafe
        if (omniRawForward != 0f || omniRawStrafe != 0f) {
            omniYaw = Math.toDegrees(MovementUtil.getDirection(player.yRot, omniRawForward, omniRawStrafe)).toFloat()
            if (rotationMode.isMode("OnTick")) {
                if (isOmniMoving()) {
                    if (sprint.value) event.sprint = true
                    if (!onTickAiming) {
                        val view = floatArrayOf(omniYaw, player.xRot)
                        rotations = view
                        RotationManager.enabled = true
                        RotationManager.correctMovement = getEffectiveMoveFix()
                        RotationManager.targetRotations = view
                        RotationManager.lastRotations = view
                        RotationManager.currentRotations = view
                    } else if (abs(Mth.wrapDegrees(omniYaw - (rotations?.get(0) ?: player.yRot))) > 67.5f) {
                        omniSuppressed = true
                    }
                }
            } else {
                val aimYaw = rotations?.get(0) ?: player.yRot
                if (abs(Mth.wrapDegrees(omniYaw - aimYaw)) > 67.5f) {
                    omniSuppressed = true
                } else {
                    omniSuppressed = false
                    if (sprint.value) event.sprint = true
                    val view = floatArrayOf(omniYaw, rotations?.get(1) ?: player.xRot)
                    rotations = view
                    RotationManager.enabled = true
                    RotationManager.correctMovement = getEffectiveMoveFix()
                    RotationManager.targetRotations = view
                    RotationManager.lastRotations = view
                    RotationManager.currentRotations = view
                    RotationManager.refreshCrosshair()
                }
            }
        } else {
            omniSuppressed = false
        }
    }

    if (slow.value && firstJumpDone) {
        event.sprint = false
    } else {
        event.sprint = event.sprint || (sprint.value && mc.options.keySprint.isDown)
    }

    if ((mode.isMode("Telly") || mode.isMode("SameY")) && MovementUtil.isMoving(player)) {
        val minDelay = minOf(jumpDelayMin.getInt(), jumpDelayMax.getInt())
        val maxDelay = maxOf(jumpDelayMin.getInt(), jumpDelayMax.getInt())
        val jumpDelay = if (minDelay == maxDelay) minDelay else Random.nextInt(minDelay, maxDelay + 1)
        event.jump = jumpDelay == 0 || cn.reverie.util.player.PlayerUtil.onGroundTicks >= jumpDelay
    }

    if (autoSneak.value && player.onGround()) {
        val level = mc.level
        if (level != null) {
            val below = BlockPos(floor(player.x).toInt(), floor(player.y - 1.0).toInt(), floor(player.z).toInt())
            if (level.getBlockState(below).canBeReplaced()) event.sneak = true
        }
    }
}
