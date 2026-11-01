package cn.reverie.util.rotation

import net.minecraft.client.Minecraft
import net.minecraft.util.Mth

class Rotation(
    var yaw: Float = 0f,
    var pitch: Float = 0f,
    var isNormalized: Boolean = false
) {
    constructor(vector: Rotation) : this(vector.yaw, vector.pitch, vector.isNormalized)

    fun add(x: Float, y: Float): Rotation = Rotation(yaw + x, pitch + y)

    fun normalize(): Rotation {
        if (isNormalized) return this
        val mc = Minecraft.getInstance()
        val sensitivity = (mc.options.sensitivity().get() * 0.6f + 0.2f).toDouble()
        val gcd = sensitivity * sensitivity * sensitivity * 8.0 * 0.15

        val currentRotation = if (RotationManager.currentRotations != null) {
            Rotation(RotationManager.currentRotations!![0], RotationManager.currentRotations!![1])
        } else if (mc.player != null) {
            Rotation(mc.player!!.yRot, mc.player!!.xRot)
        } else {
            Rotation(0f, 0f)
        }

        val diff = currentRotation.rotationDeltaTo(this)
        val g1 = (Math.round(diff.deltaYaw / gcd) * gcd).toFloat()
        val g2 = (Math.round(diff.deltaPitch / gcd) * gcd).toFloat()

        val newYaw = currentRotation.yaw + g1
        val newPitch = (currentRotation.pitch + g2).coerceIn(-90f, 90f)

        return Rotation(newYaw, newPitch, true)
    }

    fun rotationDeltaTo(other: Rotation): RotationDelta {
        return RotationDelta(
            Mth.wrapDegrees(other.yaw - this.yaw),
            Mth.wrapDegrees(other.pitch - this.pitch)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is Rotation) {
            return yaw == other.yaw && pitch == other.pitch
        }
        return false
    }

    override fun hashCode(): Int {
        var result = yaw.hashCode()
        result = 31 * result + pitch.hashCode()
        return result
    }

    override fun toString(): String = "Rotation(yaw=$yaw, pitch=$pitch)"

    data class RotationDelta(val deltaYaw: Float, val deltaPitch: Float)

    companion object {
        @JvmField
        val ZERO = Rotation(0f, 0f)
    }
}
