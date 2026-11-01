package cn.reverie.module.combat.criticals

import cn.reverie.util.network.ProtocolUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Fits a second critical attack into the remaining falling frames of one jump. */
internal object CriticalLowTimer {
    private const val MAX_PREDICTION_TICKS = 40
    private const val NANOS_PER_MILLISECOND = 1_000_000.0

    private var hitsThisJump = 0
    private var firstHitNanos = 0L
    private var cadenceMillis = 0L
    private var damageRecoveryTicks = 0
    private var waitForWeaponCharge = false
    private var secondHitNanos = 0L
    private var tickTargetMillis = 50f
    private var serverTickMillis = 50.0

    var waiting = false
        private set

    val suffix: String
        get() = "Low Timer " + String.format(Locale.ROOT, "%.2fx", serverTickMillis / tickTargetMillis)

    fun allowsAttack(): Boolean = hitsThisJump < 2 && (!waiting || System.nanoTime() >= secondHitNanos)

    fun onCriticalAttack(player: LocalPlayer, target: LivingEntity, attackIntervalMillis: Long, requireWeaponCharge: Boolean) {
        hitsThisJump = (hitsThisJump + 1).coerceAtMost(2)
        if (hitsThisJump != 1) {
            stopWaiting()
            return
        }
        firstHitNanos = System.nanoTime()
        cadenceMillis = attackIntervalMillis
        // Equal-damage hits need the target's damage immunity to expire, not just another click.
        damageRecoveryTicks = maxOf(10, target.hurtDuration, target.invulnerableTime - 10)
        waitForWeaponCharge = !ProtocolUtil.is1_8OrOlder() || requireWeaponCharge
        waiting = true
        refresh(player)
    }

    /** Recompute only at physics boundaries; changing duration every render distorts tick accumulation. */
    fun refresh(player: LocalPlayer, attackIntervalMillis: Long = cadenceMillis, requireWeaponCharge: Boolean = waitForWeaponCharge) {
        if (player.onGround()) {
            reset()
            return
        }
        if (!waiting) return
        if (player.deltaMovement.y >= 0.0 || player.fallDistance <= 0.0 || player.hasEffect(MobEffects.LEVITATION)) {
            stopWaiting()
            return
        }

        cadenceMillis = attackIntervalMillis
        waitForWeaponCharge = !ProtocolUtil.is1_8OrOlder() || requireWeaponCharge
        serverTickMillis = player.level().tickRateManager().millisecondsPerTick().toDouble().coerceAtLeast(1.0)
        val weaponTicks = if (waitForWeaponCharge) ceil(player.currentItemAttackStrengthDelay.toDouble()) else 0.0
        if (!weaponTicks.isFinite()) {
            stopWaiting()
            return
        }
        // One server tick covers the unknown phase of the server's cooldown counter.
        val recoveryMillis = (maxOf(damageRecoveryTicks.toDouble(), weaponTicks) + 1.0) * serverTickMillis
        val delayMillis = maxOf(cadenceMillis.toDouble(), recoveryMillis)
        secondHitNanos = firstHitNanos + (delayMillis * NANOS_PER_MILLISECOND).toLong()

        val fallingFrames = predictFallingFrames(player)
        // Keep one falling frame for a movement/transaction boundary before landing.
        // Even the last airborne position has an attack opportunity before the next travel.
        val attackFrames = (fallingFrames - 1).coerceAtLeast(1)
        val tickCredit = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(true).toDouble().coerceIn(0.0, 0.9999)
        val remainingTickBudget = (attackFrames - tickCredit).coerceAtLeast(0.0001)
        val remainingMillis = ((secondHitNanos - System.nanoTime()) / NANOS_PER_MILLISECOND).coerceAtLeast(0.0)
        tickTargetMillis = maxOf(serverTickMillis, ceil((remainingMillis + serverTickMillis) / remainingTickBudget)).toFloat()
    }

    fun applyTickTargetMillis(current: Float): Float = if (waiting) maxOf(current, tickTargetMillis) else current

    fun applyAttackStrengthScale(player: LocalPlayer, partialTicks: Float, vanilla: Float): Float {
        if (!waiting) return vanilla
        // Server cooldown keeps advancing in real time when only the client timer is slowed.
        val elapsedMillis = (System.nanoTime() - firstHitNanos) / NANOS_PER_MILLISECOND
        val serverTicks = floor(elapsedMillis.coerceAtLeast(0.0) / serverTickMillis)
        val charge = ((serverTicks + partialTicks) / player.currentItemAttackStrengthDelay).coerceIn(0.0, 1.0)
        return maxOf(vanilla, charge.toFloat())
    }

    fun stopWaiting() {
        waiting = false
        firstHitNanos = 0L
        secondHitNanos = 0L
        tickTargetMillis = 50f
    }

    fun reset() {
        stopWaiting()
        hitsThisJump = 0
        cadenceMillis = 0L
        damageRecoveryTicks = 0
        waitForWeaponCharge = false
        serverTickMillis = 50.0
    }

    private fun predictFallingFrames(player: LocalPlayer): Int {
        var box = player.boundingBox
        var velocity = player.deltaMovement
        val gravity = if (player.hasEffect(MobEffects.SLOW_FALLING)) minOf(player.gravity, 0.01) else player.gravity
        val dragModifier = player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER).toFloat()
        val horizontalDrag = (1f - (1f - 0.91f) * dragModifier).coerceIn(0f, 1f).toDouble()
        val verticalDrag = (1f - (1f - 0.98f) * dragModifier).coerceIn(0f, 1f).toDouble()
        val level = player.level()

        for (step in 0 until MAX_PREDICTION_TICKS) {
            val colliders = level.getEntityCollisions(player, box.expandTowards(velocity))
            val movement = Entity.collideBoundingBox(player, velocity, box, level, colliders)
            if (velocity.y < 0.0 && abs(movement.y - velocity.y) > 1.0E-7) return step
            box = box.move(movement)
            val nextX = if (abs(movement.x - velocity.x) > 1.0E-7) 0.0 else velocity.x
            val nextZ = if (abs(movement.z - velocity.z) > 1.0E-7) 0.0 else velocity.z
            velocity = Vec3(nextX, velocity.y - gravity, nextZ)
            if (!player.shouldDiscardFriction()) velocity = velocity.multiply(horizontalDrag, verticalDrag, horizontalDrag)
        }
        return MAX_PREDICTION_TICKS
    }
}
