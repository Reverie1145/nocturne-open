package cn.reverie.module.combat.killaura

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KillAuraRotationMixerChecks {
    @Test
    fun turnCurvesAreBoundedAndDistinct() {
        val signatures = KillAuraRotationMixer.turns.map { mode ->
            (0..59).map { tick -> KillAuraRotationMixer.speed(mode, tick, 120.0, 25.0, 180.0, 14.0, 45.0) }
        }
        assertEquals(10, signatures.toSet().size)
        assertTrue(signatures.flatten().all { it in 25.0..180.0 })
        assertNotEquals(120.0, KillAuraRotationMixer.speed("Fast", 0, 120.0, 25.0, 180.0, 14.0, 45.0))
    }

    @Test
    fun aimPathsStayInsideTargetBox() {
        for (mode in KillAuraRotationMixer.points) for (tick in 0..59) {
            val (x, y, z) = KillAuraRotationMixer.point(mode, tick, 14.0, 0.25)
            assertTrue(x in 0.0..1.0 && y in 0.0..1.0 && z in 0.0..1.0, "$mode at $tick")
        }
    }
}
