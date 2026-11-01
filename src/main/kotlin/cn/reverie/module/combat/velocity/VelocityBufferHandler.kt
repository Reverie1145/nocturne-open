package cn.reverie.module.combat.velocity

import cn.reverie.module.combat.*

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import cn.reverie.util.rotation.VecCalculation
import cn.reverie.module.world.Scaffold
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.hypot
internal fun Velocity.holdingInbound(): Boolean = (mode.isMode("Delay") && delay > 0) || buffering
    internal fun Velocity.isRealKnockback(movement: Vec3): Boolean {
        return hypot(movement.x, movement.z) >= 0.02 || abs(movement.y) >= 0.05
    }

    internal fun Velocity.queueInbound(event: PacketEvent, reason: String = "buffer packet") {
        try {
            @Suppress("UNCHECKED_CAST")
            packetQueue.add(event.packet as Packet<ClientGamePacketListener>)
            event.cancel()
            if (mode.isMode("Buffer") || buffering) {
                bufferLog("queued inbound packet=${packetLabel(event.packet)} reason=$reason cancelled=${event.isCancelled} queue=${packetQueue.size}")
            }
        } catch (e: Exception) {
            if (mode.isMode("Buffer") || buffering) {
                bufferLog("failed to queue inbound packet=${packetLabel(event.packet)} reason=$reason: ${e.javaClass.simpleName}: ${e.message}")
            }
            e.printStackTrace()
        }
    }

    @Synchronized
    internal fun Velocity.onBufferPacket(event: PacketEvent, player: LocalPlayer, packet: Packet<*>) {
        if (!event.isReceive) return
        bufferLog("receive packet=${packetLabel(packet)} cancelled=${event.isCancelled} player=${player.id} ${bufferState()}")

        if (packet is ClientboundDisconnectPacket) {
            bufferLog("disconnect received; reset all velocity buffers")
            reset()
            return
        }

        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundRespawnPacket) {
            bufferLog("world/position transition packet=${packetLabel(packet)}; flush buffered packets then reset buffer state")
            flush()
            resetBuffer("${packet.javaClass.simpleName} received")
            return
        }

        if (packet is ClientboundKeepAlivePacket) {
            bufferLog("keep-alive passed through without buffering")
            return
        }

        // Explosions also carry world changes, and large impulses are difficult
        // to predict while withheld. Keep them on the server's packet timeline.
        val largePlayerImpulse = packet is ClientboundSetEntityMotionPacket &&
            packet.id == player.id && packet.movement.lengthSqr() >= LARGE_KNOCKBACK_THRESHOLD_SQUARED
        if (packet is ClientboundExplodePacket || largePlayerImpulse) {
            bufferLog("safety packet=${packetLabel(packet)} largePlayerImpulse=$largePlayerImpulse buffering=$buffering; bypass knockback buffering")
            if (buffering) {
                flush()
                resetBuffer("safety packet ${packet.javaClass.simpleName}")
            }
            return
        }

        if (buffering) {
            VelocityBufferTargets.observe(packet, player.level(), player.id)
            queueInbound(event, "hold active; preserve packet ordering")
            return
        }

        when (packet) {
            is ClientboundSetEntityMotionPacket -> {
                // A position correction does not invalidate a later velocity packet.
                // Only an explosion can make a nearby motion packet a duplicate impulse.
                val explosionAgeMs = System.currentTimeMillis() - bufferLastExplosionAt
                if (packet.id == player.id && explosionAgeMs >= 100) {
                    onBufferKnockback(event, player, packet.movement)
                } else if (packet.id != player.id) {
                    bufferLog("motion ignored: entity=${packet.id} is not local player=${player.id}")
                } else {
                    bufferLog("motion ignored near explosion update: explosionAgeMs=$explosionAgeMs required=100")
                }
            }
        }
    }

    internal fun Velocity.onBufferKnockback(event: PacketEvent, player: LocalPlayer, movement: Vec3) {
        bufferLog("local motion candidate movement=$movement horizontal=${hypot(movement.x, movement.z)} vertical=${movement.y} onGround=${player.onGround()} hurtTime=${player.hurtTime}")
        bufferLog("settings maxTicks=${bufferMaxTicks.value.toInt()} attackReduce=${bufferAttackReduce.value} jumpResetPending=$bufferJumpPending jumpedThisHurt=$bufferJumpedThisHurt")
        if (!isRealKnockback(movement)) {
            bufferLog("motion ignored: below knockback threshold (horizontal>=0.02 or |vertical|>=0.05)")
            return
        }
        // Squeezing and landing/fall damage can have a downward motion packet.
        // Fall damage sets hurtTime, so hurtTime alone cannot identify combat.
        // Do not turn a grounded downward impulse into a jump-reset or hold
        // unrelated transactions; leave normal upward combat KB unchanged.
        if (movement.y < 0.0 && (player.onGround() || player.hurtTime <= 0)) {
            bufferLog("motion ignored: downward squeeze/landing impulse (y=${movement.y}, onGround=${player.onGround()}, hurtTime=${player.hurtTime})")
            return
        }
        val blocked = blockedReason(player)
        if (blocked != null) {
            bufferLog("motion ignored: player state blocks Buffer mode ($blocked)")
            return
        }
        if (player.onGround()) {
            if (!bufferJumpedThisHurt) {
                bufferJumpPending = true
                bufferLog("ground knockback: jump reset queued")
            } else {
                bufferLog("ground knockback: jump reset skipped because already used this hurt")
            }
            if (bufferAttackReduce.value) {
                bufferReducePending = true
                bufferLog("ground knockback: sprint-hit reduction queued")
            } else {
                bufferLog("ground knockback: sprint-hit reduction disabled")
            }
            return
        }
        bufferTicks = 0
        bufferStartedAtNanos = System.nanoTime()
        VelocityBufferTargets.reset()
        queueInbound(event, "airborne local knockback; delay until landing or limit")
        buffering = event.isCancelled
        bufferLog("airborne knockback ${if (buffering) "started" else "failed to start"}; cancelled=${event.isCancelled} ${bufferState()}")
    }

    internal fun Velocity.onBufferUpdate(player: LocalPlayer) {
        if (player.hurtTime <= 0) {
            if (bufferJumpedThisHurt) bufferLog("hurt window ended; jump-reset availability restored")
            bufferJumpedThisHurt = false
        }
        if (buffering) {
            bufferLog("update: still holding inbound packets; hurtTime=${player.hurtTime} ${bufferState()}")
            clearBufferAttack("inbound hold active during update")
            return
        }

        if (bufferReducePending) {
            bufferLog("update: consume sprint-hit reduction request; KillAura.attackedThisTick=${KillAura.attackedThisTick}")
            bufferReducePending = false
            clearBufferAttack("replacing target for pending reduction")
            if (bufferAttackReduce.value && !KillAura.attackedThisTick) {
                bufferAttackTarget = bufferReduceTarget(player)
                bufferLog("update: reduction target selection result=${entityLabel(bufferAttackTarget)}")
            } else {
                bufferLog("update: reduction target selection skipped (enabled=${bufferAttackReduce.value}, KillAura.attackedThisTick=${KillAura.attackedThisTick})")
            }
        }

        val target = bufferAttackTarget ?: return
        if (KillAura.attackedThisTick) {
            bufferLog("update: cancel queued reduction because KillAura already attacked this tick target=${entityLabel(target)}")
            clearBufferAttack("KillAura already attacked this tick")
            return
        }
        if (bufferAimSent) {
            val attacked = tryBufferAttackReduce(player, target)
            bufferLog("update: queued reduction attack result=$attacked target=${entityLabel(target)}")
            clearBufferAttack(if (attacked) "reduction attack sent" else "reduction attack not sent")
        }
    }

    internal fun Velocity.bufferReduceTarget(player: LocalPlayer): LivingEntity? {
        if (Scaffold.enabled) {
            bufferLog("target selection rejected: Scaffold is enabled")
            return null
        }
        val blocked = blockedReason(player)
        if (blocked != null) {
            bufferLog("target selection rejected: player state blocks reduction ($blocked)")
            return null
        }
        if (!player.isSprinting) {
            bufferLog("target selection rejected: player is not sprinting")
            return null
        }
        if (player.foodData.foodLevel <= 6) {
            bufferLog("target selection rejected: food level=${player.foodData.foodLevel} (must be > 6)")
            return null
        }
        val candidate = if (KillAura.enabled) KillAura.target else Minecraft.getInstance().crosshairPickEntity as? LivingEntity
        if (candidate == null) {
            bufferLog("target selection rejected: no ${if (KillAura.enabled) "KillAura" else "crosshair"} living-entity target")
            return null
        }
        if (!candidate.isAlive) {
            bufferLog("target selection rejected: target=${entityLabel(candidate)} is not alive")
            return null
        }
        if (candidate === player) {
            bufferLog("target selection rejected: candidate is local player")
            return null
        }
        if (Backtrack.isHolding(candidate)) {
            bufferLog("target selection rejected: Backtrack is holding target=${entityLabel(candidate)}")
            return null
        }
        val point = bufferAttackPoint(player, candidate)
        if (point == null) return null
        bufferLog("target selection accepted target=${entityLabel(candidate)} point=$point")
        return candidate
    }

    internal fun Velocity.bufferAttackPoint(player: LocalPlayer, target: LivingEntity): Vec3? {
        if (!target.isAlive) {
            bufferLog("attack point unavailable: target=${entityLabel(target)} is not alive")
            return null
        }
        if (target.level() !== player.level()) {
            bufferLog("attack point unavailable: target=${entityLabel(target)} is in another level")
            return null
        }
        if (Backtrack.isHolding(target)) {
            bufferLog("attack point unavailable: Backtrack is holding target=${entityLabel(target)}")
            return null
        }
        val distance = VecCalculation.newGetDistanceToEntityBoxSafe(player, target, 0.0)
        if (distance > 3.0) {
            bufferLog("attack point unavailable: target=${entityLabel(target)} distance=$distance exceeds 3.0")
            return null
        }
        return VecCalculation.newGetPointToEntityBoxSafe(player, target, 0.0)
    }

    internal fun Velocity.tryBufferAttackReduce(player: LocalPlayer, target: LivingEntity): Boolean {
        if (bufferReduceTarget(player) !== target) {
            bufferLog("reduction attack rejected: target=${entityLabel(target)} is no longer the selected target")
            return false
        }
        val point = bufferAttackPoint(player, target) ?: run {
            bufferLog("reduction attack rejected: no valid attack point for target=${entityLabel(target)}")
            return false
        }
        val sentRotation = RotationManager.serverRotation ?: run {
            bufferLog("reduction attack rejected: server rotation is unavailable")
            return false
        }
        val rotationDifference = RotationUtil.getRotationDifference(sentRotation, RotationUtil.vecToRotation(point))
        if (rotationDifference > 15.0) {
            bufferLog("reduction attack rejected: rotation difference=$rotationDifference exceeds 15 degrees target=${entityLabel(target)}")
            return false
        }
        if (KillAura.enabled) {
            val attacked = KillAura.attackForVelocity(player, target)
            bufferLog("reduction attack delegated to KillAura result=$attacked target=${entityLabel(target)}")
            return attacked
        }
        val gameMode = Minecraft.getInstance().gameMode ?: run {
            bufferLog("reduction attack rejected: game mode is unavailable")
            return false
        }
        val before = player.deltaMovement
        val wasSprinting = player.isSprinting
        gameMode.attack(player, target)
        reconcileBufferAttackSlow(player, before, wasSprinting)
        player.swing(InteractionHand.MAIN_HAND)
        bufferLog("reduction attack sent target=${entityLabel(target)} point=$point rotationDifference=$rotationDifference wasSprinting=$wasSprinting motionBefore=$before motionAfter=${player.deltaMovement}")
        return true
    }

    internal fun Velocity.onBufferMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!bufferJumpPending) return
        val blocked = blockedReason(player)
        if (player.onGround() && !bufferJumpedThisHurt && blocked == null) {
            event.jump = true
            bufferJumpPending = false
            bufferJumpedThisHurt = true
            bufferLog("jump input applied; hurtTime=${player.hurtTime} jumpPending=false")
        } else {
            bufferLog("jump input remains pending: onGround=${player.onGround()} alreadyJumpedThisHurt=$bufferJumpedThisHurt blocked=$blocked")
        }
    }

    internal fun Velocity.velocityBlocked(player: LocalPlayer): Boolean = blockedReason(player) != null

    internal fun Velocity.blockedReason(player: LocalPlayer): String? = when {
        player.isPassenger -> "passenger"
        player.abilities.flying -> "flying"
        player.isFallFlying -> "fall-flying"
        player.isInWater -> "in-water"
        player.isInLava -> "in-lava"
        player.onClimbable() -> "climbable"
        player.isShiftKeyDown -> "sneaking"
        else -> null
    }

    internal fun Velocity.bufferState(): String = "state{buffering=$buffering,ticks=$bufferTicks,queued=${packetQueue.size},jumpPending=$bufferJumpPending,reducePending=$bufferReducePending,jumpedThisHurt=$bufferJumpedThisHurt,target=${entityLabel(bufferAttackTarget)},aimPrepared=$bufferAimPrepared,aimSent=$bufferAimSent}"

    internal fun Velocity.entityLabel(entity: LivingEntity?): String = entity?.let { "${it.javaClass.simpleName}#${it.id}" } ?: "none"

    internal fun Velocity.packetLabel(packet: Packet<*>): String = when (packet) {
        is ClientboundSetEntityMotionPacket -> "${packet.javaClass.simpleName}(entity=${packet.id},movement=${packet.movement})"
        else -> packet.javaClass.simpleName
    }

    internal fun Velocity.bufferLog(message: String) {
        if (bufferDebugLogs.value) LOGGER.info("[Velocity/Buffer] {}", message)
    }
