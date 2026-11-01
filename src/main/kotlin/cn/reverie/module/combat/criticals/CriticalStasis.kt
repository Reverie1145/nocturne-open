package cn.reverie.module.combat.criticals

import cn.reverie.mixin.entity.LocalPlayerAccessor
import cn.reverie.util.network.ProtocolUtil
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ClientboundExplodePacket
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket
import net.minecraft.network.protocol.game.ClientboundRespawnPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket
import net.minecraft.world.entity.player.Input
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicBoolean

/** Pauses each transmitted falling frame for one tick, then requires a fresh falling frame. */
internal object CriticalStasis {
    @Volatile private var owner: LocalPlayer? = null
    @Volatile private var frozen = false
    private val interrupted = AtomicBoolean(false)
    private var releaseTick = 0
    private var fallingFrame = 0L
    private var lastHeldFrame = -1L
    private var frozenPosition = Vec3.ZERO
    private var resumeVelocity = Vec3.ZERO

    private var sentPosition: Vec3? = null
    private var sentInput = Input.EMPTY
    private var sentSprint = false
    private var movementSprint = false
    private var sentOnGround = true
    private var sentFalling = false
    private var tickClosed = true
    @Volatile private var awaitingTransaction = false
    @Volatile private var transactionSeen = false

    val holding: Boolean
        // Keep physics and packet suppression together for the whole tick. A Netty
        // interruption is consumed at the next player tick, not halfway through it.
        get() = frozen && owner === Minecraft.getInstance().player

    val hasPendingInterruption: Boolean
        get() = interrupted.get()

    fun attach(player: LocalPlayer) {
        reset()
        owner = player
        val accessor = player as LocalPlayerAccessor
        sentInput = accessor.nocturneLastSentInput()
        sentSprint = accessor.nocturneWasSprinting()
        movementSprint = sentSprint
    }

    fun update(player: LocalPlayer, canPrepare: Boolean) {
        if (owner !== player) attach(player)
        if (interrupted.getAndSet(false)) {
            stop(player, restoreVelocity = false)
            // A correction/impulse invalidates the old falling frame. Wait for fresh movement.
            sentPosition = null
            sentFalling = false
            return
        }
        if (frozen) {
            val displaced = player.position().distanceToSqr(frozenPosition) > 1.0E-12
            if (!canPrepare || player.tickCount >= releaseTick || player.onGround() ||
                player.fallDistance <= 0.0 || player.hurtTime > 0 || displaced) {
                stop(player, restoreVelocity = !displaced && player.hurtTime == 0)
            }
            return
        }
        if (!canPrepare || fallingFrame == lastHeldFrame || player.hurtTime > 0 || player.deltaMovement.y >= 0.0 ||
            !hasFallingFrame(player)) return

        frozenPosition = player.position()
        resumeVelocity = player.deltaMovement
        releaseTick = player.tickCount + 1
        lastHeldFrame = fallingFrame
        frozen = true
    }

    fun hasFallingFrame(player: LocalPlayer): Boolean =
        owner === player && !interrupted.get() && tickClosed && sentFalling && !sentOnGround &&
            !sentSprint && !movementSprint &&
            sentPosition?.distanceToSqr(player.position())?.let { it <= 1.0E-12 } == true

    fun canAttackWithoutPost(): Boolean = !awaitingTransaction || !transactionSeen && !holding

    fun suppresses(packet: Packet<*>): Boolean {
        if (!holding) return false
        return packet is ServerboundMovePlayerPacket || packet is ServerboundPlayerInputPacket ||
            packet is ServerboundClientTickEndPacket || packet is ServerboundPlayerCommandPacket &&
            (packet.action == ServerboundPlayerCommandPacket.Action.START_SPRINTING ||
                packet.action == ServerboundPlayerCommandPacket.Action.STOP_SPRINTING)
    }

    /** Called after all packet listeners have accepted the packet, including no-event sends. */
    fun onSent(packet: Packet<*>) {
        val player = owner ?: return
        if (player !== Minecraft.getInstance().player) return
        when (packet) {
            is ServerboundPlayerInputPacket -> sentInput = packet.input()
            is ServerboundPlayerCommandPacket -> when (packet.action) {
                ServerboundPlayerCommandPacket.Action.START_SPRINTING -> sentSprint = true
                ServerboundPlayerCommandPacket.Action.STOP_SPRINTING -> sentSprint = false
                else -> Unit
            }
            is ServerboundMovePlayerPacket -> {
                if (packet.hasPosition()) {
                    val next = Vec3(packet.getX(player.x), packet.getY(player.y), packet.getZ(player.z))
                    sentFalling = sentPosition?.let { next.y < it.y && !packet.isOnGround } == true
                    sentPosition = next
                    if (sentFalling) fallingFrame++
                }
                sentOnGround = packet.isOnGround
                movementSprint = sentSprint
                tickClosed = false
                awaitingTransaction = true
            }
            is ServerboundClientTickEndPacket -> {
                // Grim treats an idle modern TickEnd as a flying boundary too.
                if (tickClosed && ProtocolUtil.targetVersion()?.newerThanOrEqualTo(ProtocolVersion.v1_21_2) != false) {
                    awaitingTransaction = true
                }
                tickClosed = true
            }
            is ServerboundPongPacket -> {
                // Grim uses nonpositive short IDs; unrelated plugin pings cannot open this window.
                if (packet.id in Short.MIN_VALUE.toInt()..0) {
                    transactionSeen = true
                    awaitingTransaction = false
                }
            }
        }
    }

    /** Receive callbacks run on Netty; defer the movement/packet transition to update(). */
    fun onReceive(packet: Packet<*>) {
        val player = owner ?: return
        if (packet is ClientboundPlayerPositionPacket || packet is ClientboundRespawnPacket ||
            packet is ClientboundSetEntityMotionPacket && packet.id == player.id ||
            packet is ClientboundExplodePacket && packet.playerKnockback().isPresent) {
            interrupted.set(true)
        }
    }

    fun afterPhysics(player: LocalPlayer) {
        // Travel is paused, so entity pushes/normalization must not accumulate extra simulated ticks.
        if (holding && owner === player && !interrupted.get()) player.deltaMovement = resumeVelocity
    }

    fun afterAttack(player: LocalPlayer) {
        // Preserve any legitimate attack slowdown instead of undoing it when movement resumes.
        if (holding && owner === player && !interrupted.get()) resumeVelocity = player.deltaMovement
    }

    fun stop(player: LocalPlayer, restoreVelocity: Boolean = true) {
        if (frozen && owner === player && Minecraft.getInstance().player === player) {
            if (restoreVelocity && !interrupted.get()) player.deltaMovement = resumeVelocity
            val accessor = player as LocalPlayerAccessor
            accessor.nocturneSetLastSentInput(sentInput)
            accessor.nocturneSetWasSprinting(sentSprint)
        }
        frozen = false
    }

    fun reset() {
        frozen = false
        interrupted.set(false)
        owner = null
        fallingFrame = 0L
        lastHeldFrame = -1L
        sentPosition = null
        sentInput = Input.EMPTY
        sentSprint = false
        movementSprint = false
        sentOnGround = true
        sentFalling = false
        tickClosed = true
        awaitingTransaction = false
        transactionSeen = false
        releaseTick = 0
        frozenPosition = Vec3.ZERO
        resumeVelocity = Vec3.ZERO
    }
}
