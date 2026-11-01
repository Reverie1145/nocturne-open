package cn.reverie.util.misc

import kotlin.test.Test
import kotlin.test.assertEquals

class ClickCadenceChecks {
    @Test
    fun schedulesCpsAndCapsBursts() {
        val cadence = ClickCadence()
        cadence.reset(1_000)
        assertEquals(0, cadence.attempts(1_049, 20.0))
        assertEquals(1, cadence.attempts(1_050, 20.0))
        assertEquals(3, cadence.attempts(1_100, 60.0))
        assertEquals(8, cadence.attempts(2_000, 100.0))
        cadence.reset(2_000)
        assertEquals(0, cadence.attempts(2_000, 100.0))
    }

    @Test
    fun holdsConfiguredAttemptRateAcrossTwentyGameTicks() {
        for (cps in listOf(8.0, 18.0, 30.0, 60.0)) {
            val cadence = ClickCadence()
            cadence.reset(10_000)
            val attempts = (1..20).sumOf { tick -> cadence.attempts(10_000 + tick * 50L, cps) }
            assertEquals(cps.toInt(), attempts, "configured $cps CPS")
        }
    }
}
