package cn.reverie.module.misc

internal object FlagDeleterRules {
    private const val ANGLE_EPSILON = 0.0001f
    private const val POSITION_EPSILON = 0.000001

    fun isRotationCorrection(yawDelta: Float, pitchDelta: Float, threshold: Float): Boolean =
        (yawDelta > ANGLE_EPSILON && yawDelta >= threshold) ||
            (pitchDelta > ANGLE_EPSILON && pitchDelta >= threshold)

    fun isSetbackCorrection(distance: Double, threshold: Double): Boolean =
        distance > POSITION_EPSILON && distance >= threshold
}
