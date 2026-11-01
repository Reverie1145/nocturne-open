package cn.reverie.module.misc

import net.minecraft.util.Mth
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlagDeleterRulesChecks {
    @Test
    fun rotationThresholdUsesShortestWrappedYawDeltaAndEitherAxis() {
        val wrappedYawDelta = abs(Mth.wrapDegrees(-179f - 179f))
        assertFalse(FlagDeleterRules.isRotationCorrection(wrappedYawDelta, 0f, 5f))
        assertTrue(FlagDeleterRules.isRotationCorrection(0f, 5f, 5f))
        assertTrue(FlagDeleterRules.isRotationCorrection(5f, 0f, 5f))
        assertTrue(FlagDeleterRules.isRotationCorrection(0.1f, 0f, 0.1f))
        assertFalse(FlagDeleterRules.isRotationCorrection(0f, 0f, 0f))
    }

    @Test
    fun setbackThresholdIsInclusive() {
        assertFalse(FlagDeleterRules.isSetbackCorrection(0.49, 0.5))
        assertTrue(FlagDeleterRules.isSetbackCorrection(0.5, 0.5))
        assertTrue(FlagDeleterRules.isSetbackCorrection(0.01, 0.01))
        assertFalse(FlagDeleterRules.isSetbackCorrection(0.0, 0.0))
    }
}
