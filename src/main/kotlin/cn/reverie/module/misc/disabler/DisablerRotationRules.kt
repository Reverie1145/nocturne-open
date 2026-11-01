package cn.reverie.module.misc.disabler

import cn.reverie.module.*
import net.minecraft.network.protocol.game.*
import net.minecraft.util.Mth
import java.util.Random
import kotlin.math.abs
import kotlin.math.roundToLong
internal object DisablerRotationRules {
    private const val EPSILON = 0.0000000001
    private val PERFECT_PATTERNS = doubleArrayOf(0.1, 0.25)
    fun shouldModifyRotation(currentYaw: Float, currentPitch: Float, lastYaw: Float, lastPitch: Float): Boolean {
        if (lastYaw == 0f && lastPitch == 0f) return false

        val yawDelta = abs(Mth.wrapDegrees(currentYaw - lastYaw).toDouble())
        val pitchDelta = abs((currentPitch - lastPitch).toDouble())

        val isStepYaw = yawDelta < 0.00001 && pitchDelta > 1.0
        val isStepPitch = pitchDelta < 0.00001 && yawDelta > 1.0

        return isStepYaw || isStepPitch
    }

    fun getModifiedRotation(yaw: Float, pitch: Float, lastYaw: Float, lastPitch: Float, random: Random): FloatArray {
        val yawDelta = abs(Mth.wrapDegrees(yaw - lastYaw).toDouble())
        val pitchDelta = abs((pitch - lastPitch).toDouble())

        var newYaw = yaw
        var newPitch = pitch

        if (yawDelta < 0.00001 && pitchDelta > 1.0) {
            newYaw = lastYaw + (random.nextGaussian() * 0.001).toFloat()
        }

        if (pitchDelta < 0.00001 && yawDelta > 1.0) {
            newPitch = lastPitch + (random.nextGaussian() * 0.001).toFloat()
        }

        return floatArrayOf(newYaw, newPitch)
    }

    fun getAntiPerfectRotation(yaw: Float, pitch: Float, lastYaw: Float, lastPitch: Float, random: Random): FloatArray {
        if (lastYaw == 0f && lastPitch == 0f) return floatArrayOf(yaw, pitch)

        val yawDelta = abs(Mth.wrapDegrees(yaw - lastYaw).toDouble())
        val pitchDelta = abs((pitch - lastPitch).toDouble())

        var newYaw = yaw
        var newPitch = pitch

        if (isNotNoRotation(yawDelta) && isPerfectPattern(yawDelta)) {
            val jitter = random.nextGaussian() * 0.005
            newYaw = yaw + jitter.toFloat()
        }

        if (isNotNoRotation(pitchDelta) && isPerfectPattern(pitchDelta)) {
            val jitter = random.nextGaussian() * 0.005
            newPitch = pitch + jitter.toFloat()
        }

        return floatArrayOf(newYaw, newPitch)
    }

    private fun isNotNoRotation(rotation: Double): Boolean {
        return abs(rotation) > EPSILON && !isIntegerMultiple(360.0, rotation)
    }

    private fun isPerfectPattern(rotation: Double): Boolean {
        if (rotation.isInfinite() || rotation.isNaN()) return false

        for (pattern in PERFECT_PATTERNS) {
            if (isIntegerMultiple(pattern, rotation)) {
                return true
            }
        }
        return false
    }

    private fun isIntegerMultiple(reference: Double, value: Double): Boolean {
        if (reference == 0.0) return abs(value) <= EPSILON
        val multiple = value / reference
        return abs(multiple - multiple.roundToLong()) <= EPSILON
    }

}
