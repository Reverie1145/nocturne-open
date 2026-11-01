package cn.reverie.util.rotation

import net.minecraft.util.Mth

enum class SmoothMode {
    LINEAR {
        override fun apply(current: Rotation, target: Rotation, rotationSpeed: Float): Rotation {
            val yawDelta = Mth.wrapDegrees(target.yaw - current.yaw)
            val pitchDelta = target.pitch - current.pitch
            val factor = Math.min(1.0f, rotationSpeed / 180.0f)

            return Rotation(
                current.yaw + yawDelta * factor,
                (current.pitch + pitchDelta * factor).coerceIn(-90.0f, 90.0f)
            )
        }
    },
    ADVANCED {
        override fun apply(current: Rotation, target: Rotation, rotationSpeed: Float): Rotation {
            val yawSpeed = rotationSpeed.toInt()
            val pitchSpeed = (rotationSpeed / 2.0f).toInt()
            if (yawSpeed <= 0 || pitchSpeed <= 0) {
                return target
            }

            val yawDelta = Mth.wrapDegrees(target.yaw - current.yaw).coerceIn(-yawSpeed.toFloat(), yawSpeed.toFloat())
            val pitchDelta = (target.pitch - current.pitch).coerceIn(-pitchSpeed.toFloat(), pitchSpeed.toFloat())

            return Rotation(
                current.yaw + yawDelta,
                (current.pitch + pitchDelta).coerceIn(-90.0f, 90.0f)
            )
        }
    };

    abstract fun apply(current: Rotation, target: Rotation, rotationSpeed: Float): Rotation
}
