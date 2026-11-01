package cn.reverie.module.combat

import cn.reverie.module.combat.velocity.*

import com.mojang.logging.LogUtils
import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.player.MoveInputEvent
import cn.reverie.module.BooleanSetting
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import cn.reverie.util.network.ProtocolUtil
import cn.reverie.util.rotation.MoveFix
import cn.reverie.util.rotation.RotationManager
import cn.reverie.util.rotation.RotationUtil
import cn.reverie.module.world.Scaffold
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
object Velocity : Module(
    name = "Velocity",
    description = "Reduce knockback when being attacked",
    category = Category.COMBAT,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    internal val LOGGER = LogUtils.getLogger()
    internal const val LARGE_KNOCKBACK_THRESHOLD_SQUARED = 0.5625

    val mode = ModeSetting("Mode", "Knockback reduction mode", "Normal", listOf("Normal", "Jump", "Delay", "Hypixel", "Buffer"))
    val velY = NumberSetting("Velocity Y", "Vertical knockback multiplier", 0.0, 0.0, 1.0, 0.01, { mode.isMode("Normal") })
    val velXZ = NumberSetting("Velocity XZ", "Horizontal knockback multiplier", 0.0, 0.0, 1.0, 0.01, { mode.isMode("Normal") })
    val delayTicks = NumberSetting("Delay Ticks", "Ticks to buffer packets in Delay mode", 3.0, 1.0, 5.0, 1.0, { mode.isMode("Delay") })
    val jump = BooleanSetting("Jump", "Jump reset knockback during delay", false, { mode.isMode("Delay") })
    val hypixelChance = NumberSetting("Jump Chance", "Chance to jump-reset grounded knockback (Hypixel)", 100.0, 1.0, 100.0, 1.0, { mode.isMode("Hypixel") })
    val hypixelSprint = BooleanSetting("Smart Sprint", "Sprint-jump only when look direction reduces XZ knockback (Hypixel)", true, { mode.isMode("Hypixel") })
    val hypixelReverse = BooleanSetting("Reverse", "Vanilla air-control against knockback for 2 ticks (Hypixel)", true, { mode.isMode("Hypixel") })
    val hypixelAttackReduce = BooleanSetting("Attack Reduce", "Vanilla sprint-hit 0.6 XZ if already sprinting (Grim AttackSlow)", true, { mode.isMode("Hypixel") })
    val bufferMaxTicks = NumberSetting("Max Ticks", "Maximum knockback delay while airborne; also capped by wall time", 4.0, 1.0, 20.0, 1.0, { mode.isMode("Buffer") })
    val bufferAttackReduce = BooleanSetting("Attack Reduce", "Vanilla sprint-hit 0.6 XZ if already sprinting (Grim AttackSlow, 1 hit)", true, { mode.isMode("Buffer") })
    val bufferServerPositionAim = BooleanSetting("Server Position Aim", "Use held server movement packets for target aim and attack checks", false, { mode.isMode("Buffer") })
    val bufferDebugLogs = BooleanSetting("Debug Logs", "Write detailed Buffer mode packet/state diagnostics to latest.log", false, { mode.isMode("Buffer") })

    @JvmField
    var delay: Int = 0

    internal val packetQueue = ConcurrentLinkedQueue<Packet<ClientGamePacketListener>>()
    internal var lag: Long = 0L
    @Volatile internal var bufferLastExplosionAt = 0L

    @Volatile internal var hypixelJumpPending = false
    @Volatile internal var hypixelSprintJump = false
    @Volatile internal var hypixelJumpedThisHurt = false
    @Volatile internal var hypixelWantJump = false
    @Volatile internal var hypixelChanceRolled = false
    @Volatile internal var hypixelReverseTicks = 0
    @Volatile internal var hypixelKbX = 0.0
    @Volatile internal var hypixelKbZ = 0.0
    @Volatile internal var hypixelSeenMotionThisHurt = false
    @Volatile internal var hypixelReducePending = false
    @Volatile internal var hypixelReducedThisHurt = false

    @Volatile internal var buffering = false
    @Volatile internal var bufferTicks = 0
    @Volatile internal var bufferStartedAtNanos = 0L
    @Volatile internal var bufferJumpPending = false
    @Volatile internal var bufferReducePending = false
    @Volatile internal var bufferJumpedThisHurt = false
    @Volatile internal var bufferAttackTarget: LivingEntity? = null
    @Volatile internal var bufferAimPrepared = false
    @Volatile internal var bufferAimSent = false

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
    }

    override val suffix: String
        get() = mode.value

    override fun onDisable() {
        flush()
        reset()
    }

    @JvmStatic
    fun reset() {
        packetQueue.clear()
        delay = 0
        lag = 0L
        bufferLastExplosionAt = 0L
        resetHypixel()
        resetBuffer()
    }

    internal fun resetBuffer(reason: String = "reset") {
        bufferLog("resetBuffer(reason=$reason) before ${bufferState()}")
        buffering = false
        VelocityBufferTargets.reset()
        bufferTicks = 0
        bufferStartedAtNanos = 0L
        bufferJumpPending = false
        bufferReducePending = false
        bufferJumpedThisHurt = false
        clearBufferAttack("buffer reset: $reason")
    }

    internal fun clearBufferAttack(reason: String = "cleared") {
        if (bufferAttackTarget != null || bufferAimPrepared || bufferAimSent) {
            bufferLog("clear attack state (reason=$reason, target=${entityLabel(bufferAttackTarget)}, aimPrepared=$bufferAimPrepared, aimSent=$bufferAimSent)")
        }
        bufferAttackTarget = null
        bufferAimPrepared = false
        bufferAimSent = false
    }

    private fun resetHypixel() {
        hypixelJumpPending = false
        hypixelSprintJump = false
        hypixelJumpedThisHurt = false
        hypixelWantJump = false
        hypixelChanceRolled = false
        hypixelReverseTicks = 0
        hypixelKbX = 0.0
        hypixelKbZ = 0.0
        hypixelSeenMotionThisHurt = false
        hypixelReducePending = false
        hypixelReducedThisHurt = false
    }

    @JvmStatic
    fun yawFromMotion(motionX: Int, motionZ: Int): Float {
        val yaw = (Math.toDegrees(Math.atan2(motionZ.toDouble(), motionX.toDouble())) + 90.0).toFloat()
        return Mth.wrapDegrees(yaw)
    }

    private fun onPacket(event: PacketEvent) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val packet = event.packet

        try {

            if (packet is ClientboundPlayerPositionPacket || packet is ClientboundExplodePacket) {
                lag = System.currentTimeMillis()
            }
            if (event.isReceive && packet is ClientboundExplodePacket) {
                bufferLastExplosionAt = System.currentTimeMillis()
            }

            if (event.isReceive && !holdingInbound()) {
                flush()
            }

            // A mode switch must not release the old buffer on Netty while
            // combat can run on the client thread in the same tick.
            if (buffering && !mode.isMode("Buffer")) {
                bufferLog("mode changed to ${mode.value} while holding inbound packets; continue draining old Buffer queue")
                onBufferPacket(event, player, packet)
                return
            }

            when (mode.value) {
                "Hypixel" -> {
                    if (!event.isReceive) return
                    if (packet is ClientboundSetEntityMotionPacket &&
                        packet.id == player.id &&
                        System.currentTimeMillis() - lag >= 100
                    ) {
                        onHypixelMotion(player, packet.movement)
                    } else if (packet is ClientboundExplodePacket) {
                        val kb = packet.playerKnockback.orElse(null)
                        if (kb != null && (kb.x != 0.0 || kb.z != 0.0 || kb.y != 0.0)) {
                            onHypixelMotion(player, kb, explode = true)
                        }
                    }
                }

                "Normal" -> {
                    if (event.isReceive && packet is ClientboundSetEntityMotionPacket) {
                        if (packet.id == player.id && System.currentTimeMillis() - lag >= 100) {
                            val xzFactor = velXZ.value
                            val yFactor = velY.value

                            if (xzFactor == 0.0 && yFactor == 0.0) {
                                event.cancel()
                            } else if (xzFactor != 1.0 || yFactor != 1.0) {
                                event.cancel()
                                val movement = packet.movement
                                val scaled = Vec3(movement.x * xzFactor, movement.y * yFactor, movement.z * xzFactor)
                                mc.execute {
                                    if (mc.player?.id == packet.id) {
                                        mc.player?.deltaMovement = scaled
                                    }
                                }
                            }
                        }
                    }
                }

                "Delay" -> {
                    if (!event.isReceive) return

                    if (delay > 0) {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            packetQueue.add(packet as Packet<ClientGamePacketListener>)
                            event.cancel()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    if (packet is ClientboundDisconnectPacket) {
                        reset()
                    }

                    if (packet is ClientboundSetEntityMotionPacket) {
                        if (packet.id == player.id && System.currentTimeMillis() - lag >= 100) {
                            val movement = packet.movement
                            val checkFall = movement.y > 0 && (movement.x != 0.0 || movement.z != 0.0)
                            if (checkFall && delay == 0) {
                                delay = delayTicks.value.toInt()
                            }
                        }
                    }
                }

                "Buffer" -> onBufferPacket(event, player, packet)
            }
        } catch (e: Exception) {
            if (mode.isMode("Buffer")) bufferLog("packet handler exception packet=${packetLabel(packet)}: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun onBeforeCombat(player: LocalPlayer) {
        if (!enabled || !buffering) return
        if (!mode.isMode("Buffer")) {
            flush()
            resetBuffer()
            return
        }

        // Apply the queued velocity before KillAura can send an attack. Otherwise
        // the local sprint-hit slowdown can be overwritten by the delayed velocity,
        // while Grim predicts the attack and velocity in the opposite order.
        bufferTicks++
        val maxTicks = bufferMaxTicks.value.toInt()
        val elapsedNanos = System.nanoTime() - bufferStartedAtNanos
        val onGround = player.onGround()
        val tickLimitReached = bufferTicks >= maxTicks
        val timeLimitReached = elapsedNanos >= maxTicks * 50_000_000L
        bufferLog("beforeCombat tick=$bufferTicks/$maxTicks elapsedMs=${elapsedNanos / 1_000_000.0} onGround=$onGround queue=${packetQueue.size}")
        if (onGround || tickLimitReached || timeLimitReached) {
            val reasons = buildList {
                if (onGround) add("landed")
                if (tickLimitReached) add("tick-limit")
                if (timeLimitReached) add("time-limit")
            }.joinToString("+")
            bufferLog("beforeCombat releasing buffered packets reason=$reasons")
            flush()
            bufferJumpPending = false
            if (bufferAttackReduce.value && !velocityBlocked(player)) {
                bufferReducePending = true
                bufferLog("sprint-hit reduction queued after release; hurtTime=${player.hurtTime}")
            } else {
                bufferLog("sprint-hit reduction not queued (enabled=${bufferAttackReduce.value}, blocked=${blockedReason(player)})")
            }
        }
    }

    fun isBufferingInbound(): Boolean = enabled && buffering

    fun usesBufferedTargetPositions(): Boolean = isBufferingInbound() && mode.isMode("Buffer") && bufferServerPositionAim.value

    internal fun bufferedHitbox(entity: Entity): AABB? =
        if (usesBufferedTargetPositions()) VelocityBufferTargets.hitbox(entity) else null

    internal fun bufferedMovement(entity: Entity): Vec3? =
        if (usesBufferedTargetPositions()) VelocityBufferTargets.movement(entity) else null

    internal fun isBufferTargetRemoved(entity: Entity): Boolean =
        usesBufferedTargetPositions() && VelocityBufferTargets.isRemoved(entity)

    @JvmStatic
    fun onLivingUpdate(player: LocalPlayer) {
        if (!enabled || !mode.isMode("Buffer") || bufferAimSent) return
        if (buffering) {
            clearBufferAttack("still buffering inbound packets")
            return
        }
        if (Scaffold.enabled || velocityBlocked(player)) {
            clearBufferAttack("scaffold=${Scaffold.enabled}, blocked=${blockedReason(player)}")
            return
        }
        val target = bufferAttackTarget ?: return
        val point = bufferAttackPoint(player, target) ?: run {
            clearBufferAttack("target became invalid before aim")
            return
        }
        // Run before KeyboardInput.tick so Silent move-fix and the outgoing
        // rotation describe the same movement tick. Do not turn the camera.
        val rotation = RotationUtil.vecToRotation(point).normalize()
        RotationManager.setView(floatArrayOf(rotation.yaw, rotation.pitch), MoveFix.Silent)
        bufferAimPrepared = true
        bufferLog("aim prepared target=${entityLabel(target)} point=$point rotation=${rotation.yaw},${rotation.pitch} moveFix=${MoveFix.Silent}")
    }

    @JvmStatic
    fun onPostMotion() {
        if (bufferAttackTarget != null && bufferAimPrepared && !bufferAimSent) {
            bufferAimSent = true
            bufferLog("aim rotation sent target=${entityLabel(bufferAttackTarget)}")
        }
    }

    @JvmStatic
    fun silentPhysicsYaw(player: LocalPlayer): Float? {
        if (!enabled || !mode.isMode("Buffer") || buffering || !bufferAimPrepared || bufferAimSent || bufferAttackTarget == null) return null
        val yaw = RotationManager.peekPacketRotation(player)[0]
        bufferLog("silent physics yaw supplied yaw=$yaw target=${entityLabel(bufferAttackTarget)}")
        return yaw
    }

    @JvmStatic
    fun reconcileBufferAttackSlow(player: LocalPlayer, before: Vec3, wasSprinting: Boolean) {
        if (!enabled || !mode.isMode("Buffer") || !bufferAttackReduce.value || !ProtocolUtil.is1_8OrOlder()) return
        if (!wasSprinting || (!bufferReducePending && bufferAttackTarget == null)) return

        // 26.2 Player.attack only performs sprint-hit slowdown when the target's
        // client-side hurt simulation succeeds. Grim's 1.8 AttackSlow instead
        // predicts it from the attack packet and previous sprint state.
        val after = player.deltaMovement
        if (player.isSprinting && abs(after.x - before.x) < 1.0E-9 && abs(after.z - before.z) < 1.0E-9) {
            player.deltaMovement = Vec3(after.x * 0.6, after.y, after.z * 0.6)
            player.isSprinting = false
            bufferLog("reconciled legacy sprint-hit slowdown before=$before after=$after corrected=${player.deltaMovement} sprinting=false")
        } else {
            bufferLog("legacy sprint-hit reconciliation unnecessary before=$before after=$after sprinting=${player.isSprinting}")
        }
    }

    @JvmStatic
    fun onUpdate(player: LocalPlayer) {
        if (!enabled) return
        if (mode.isMode("Delay")) {
            if (delay >= 1) {
                delay--
                if (delay == 0) {
                    flush()
                }
            }
        }

        if (mode.isMode("Hypixel")) {
            onHypixelUpdate(player)
        } else if (hypixelJumpPending || hypixelReverseTicks > 0) {
            resetHypixel()
        }

        if (mode.isMode("Buffer")) {
            onBufferUpdate(player)
        } else if (buffering || bufferJumpPending || bufferReducePending) {
            bufferLog("mode changed to ${mode.value}; cleaning up old Buffer state")
            if (buffering) flush()
            resetBuffer("mode changed to ${mode.value}")
        }
    }

    @JvmStatic
    fun onMoveInput(player: LocalPlayer, event: MoveInputEvent) {
        if (!enabled) return
        if (mode.isMode("Jump") || (mode.isMode("Delay") && jump.value)) {
            if (player.onGround() && player.hurtTime == 9) {
                event.jump = true
            }
        }
        if (mode.isMode("Hypixel")) {
            onHypixelMoveInput(player, event)
        }
        if (mode.isMode("Buffer")) {
            onBufferMoveInput(player, event)
        }
    }

    internal fun hypixelBlocked(player: LocalPlayer): Boolean {
        return blockedReason(player) != null
    }

    @Synchronized
    fun flush() {
        val queuedBeforeFlush = packetQueue.size
        val wasBuffering = buffering
        val packets = ArrayList<Packet<ClientGamePacketListener>>(queuedBeforeFlush)
        while (true) packets.add(packetQueue.poll() ?: break)
        delay = 0
        buffering = false
        VelocityBufferTargets.reset()
        bufferTicks = 0
        bufferStartedAtNanos = 0L
        if (packets.isEmpty()) return

        val mc = Minecraft.getInstance()
        val connection = mc.connection ?: run {
            bufferLog("flush discarded queued=${packets.size}: connection is unavailable")
            return
        }
        bufferLog("flush scheduled queued=${packets.size} wasBuffering=$wasBuffering")
        // Run the whole batch on the client thread. Handling non-ping packets on
        // Netty only schedules them there, while handling pings immediately sends
        // their acknowledgements and overtakes the queued velocity/teleport.
        val deliver = {
            if (mc.connection !== connection) {
                bufferLog("flush discarded queued=${packets.size}: connection changed")
            } else {
                var handled = 0
                var failed = 0
                for (packet in packets) {
                    try {
                        packet.handle(connection)
                        handled++
                        bufferLog("flush handled packet=${packetLabel(packet)}")
                    } catch (e: Exception) {
                        failed++
                        bufferLog("flush failed packet=${packetLabel(packet)}: ${e.javaClass.simpleName}: ${e.message}")
                    }
                }
                bufferLog("flush complete handled=$handled failed=$failed")
            }
        }
        if (mc.isSameThread) deliver() else mc.execute(deliver)
    }

}
