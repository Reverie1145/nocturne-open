package cn.reverie.module.combat

import cn.reverie.module.*
import cn.reverie.module.player.Teams
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.util.UUID
import kotlin.math.*

object AimAssist : Module("AimAssist", "Smoothly assists aim while attacking", Category.COMBAT) {
    val range = NumberSetting("Range", "Maximum target distance", 4.0, 1.0, 8.0, 0.1)
    val fov = NumberSetting("FOV", "Maximum yaw difference", 60.0, 5.0, 180.0, 1.0)
    val speed = NumberSetting("Speed", "Maximum turn per tick", 8.0, 0.5, 30.0, 0.5)
    val strength = NumberSetting("Strength", "Fraction of error corrected", 0.35, 0.05, 1.0, 0.05)
    val noiseEnabled = BooleanSetting("Noise", "Add smooth, zero-mean angular noise", false)
    val noiseAmount = NumberSetting("Noise Amount", "Maximum smooth noise in degrees", 0.35, 0.0, 3.0, 0.05) { noiseEnabled.value }
    val onlyClicking = BooleanSetting("Only Clicking", "Assist only while attack is held", true)
    val playersOnly = BooleanSetting("Players Only", "Ignore non-player entities", true)
    private var lastFrameNanos = 0L
    private var currentTargetId: UUID? = null

    /** Apply aim on render frames, not 20Hz player ticks, to avoid visible stepping. */
    fun onRenderFrame(player: LocalPlayer) {
        val nowNanos = System.nanoTime()
        val deltaSeconds = if (lastFrameNanos == 0L) {
            1.0 / 60.0
        } else {
            ((nowNanos - lastFrameNanos) * 1.0e-9).coerceIn(0.001, 0.05)
        }
        lastFrameNanos = nowNanos

        val mc = Minecraft.getInstance()
        if (!enabled || mc.gui.screen() != null || (onlyClicking.value && !mc.options.keyAttack.isDown) || KillAura.enabled) return
        val candidates = player.level().getEntities(player, player.boundingBox.inflate(range.value))
            .filterIsInstance<LivingEntity>()
            .filter { it.isAlive && !it.isSpectator && !AntiBot.isBot(it) && !Teams.isTeammate(it) && (!playersOnly.value || it is Player) && player.distanceTo(it) <= range.value && player.hasLineOfSight(it) }
            .mapNotNull { entity ->
                val aim = entity.boundingBox.center.subtract(player.eyePosition)
                val yaw = Math.toDegrees(atan2(aim.z, aim.x)).toFloat() - 90f
                val pitch = -Math.toDegrees(atan2(aim.y, hypot(aim.x, aim.z))).toFloat()
                val error = Mth.wrapDegrees(yaw - player.yRot)
                if (abs(error) > fov.value) null else Triple(entity, error, pitch - player.xRot)
            }
        val target = candidates.firstOrNull { it.first.uuid == currentTargetId }
            ?: candidates.minByOrNull { abs(it.second) + abs(it.third) }
        if (target == null) {
            currentTargetId = null
            return
        }
        currentTargetId = target.first.uuid

        // Preserve the existing per-tick tuning while making it frame-rate independent.
        val tickFraction = (deltaSeconds * 20.0).toFloat()
        val correction = (1.0 - (1.0 - strength.value).pow(tickFraction.toDouble())).toFloat()
        val maxTurn = speed.getFloat() * tickFraction
        val now = nowNanos * 1.0e-9
        val phase = if (noiseEnabled.value) phaseFor(target.first) else 0.0
        val noise = if (noiseEnabled.value) {
            smoothNoise(now, phase) * noiseAmount.getFloat()
        } else 0f
        val yawStep = ((target.second + noise) * correction).coerceIn(-maxTurn, maxTurn)
        val pitchNoise = if (noiseEnabled.value) {
            smoothNoise(now, phase + 2.17) * noiseAmount.getFloat() * 0.5f
        } else 0f
        val pitchStep = ((target.third + pitchNoise) * correction).coerceIn(-maxTurn, maxTurn)

        player.yRot = Mth.wrapDegrees(player.yRot + yawStep)
        player.xRot = (player.xRot + pitchStep).coerceIn(-90f, 90f)
    }

    private fun phaseFor(entity: LivingEntity): Double {
        val bits = entity.uuid.mostSignificantBits xor entity.uuid.leastSignificantBits
        return (bits and 0xFFFFL).toDouble() / 0xFFFF.toDouble() * Math.PI * 2.0
    }

    /** Continuous, bounded noise instead of per-tick randomness, so it remains visually smooth. */
    private fun smoothNoise(timeSeconds: Double, phase: Double): Float =
        (sin(timeSeconds * 0.65 + phase) * 0.65 + sin(timeSeconds * 0.27 + phase * 1.37) * 0.35).toFloat()
}
