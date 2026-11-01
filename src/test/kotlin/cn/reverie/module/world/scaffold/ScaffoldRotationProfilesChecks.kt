package cn.reverie.module.world.scaffold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScaffoldRotationProfilesChecks {
    @Test
    fun allHundredProfilesHaveDistinctSpeedAndPointTrajectories() {
        val profiles = ScaffoldRotationProfiles
        assertEquals(100, profiles.modes.size)
        assertEquals(100, profiles.modes.toSet().size)
        assertEquals(100, profiles.turns.flatMap { turn -> profiles.paths.map { path -> profiles.index(turn, path) } }.toSet().size)
        val signatures = profiles.modes.indices.map { index ->
            (0..39).map { tick ->
                Triple(profiles.speed(index, tick), profiles.point(index, tick).first, profiles.point(index, tick).second)
            }
        }
        assertEquals(100, signatures.toSet().size)
    }

    @Test
    fun eachOfTheHundredControlsChangesOnlyItsProfileAndDefaultsRemainNeutral() {
        val profiles = ScaffoldRotationProfiles
        for (index in profiles.modes.indices) {
            val baselineSpeed = (0..39).map { profiles.speed(index, it) }
            val baselinePoint = (0..39).map { profiles.point(index, it) }
            val default = profiles.defaultTuning(index)
            val range = profiles.tuningRange(index)
            assertTrue(default in range)
            val changed = if (default != range.start) range.start else range.endInclusive
            val changedSpeed = (0..39).map { profiles.speed(index, it, changed) }
            val changedPoint = (0..39).map { profiles.point(index, it, changed) }
            assertNotEquals(baselineSpeed to baselinePoint, changedSpeed to changedPoint, profiles.modes[index])
            assertEquals(baselineSpeed, (0..39).map { profiles.speed(index, it, default) })
            assertEquals(baselinePoint, (0..39).map { profiles.point(index, it, default) })
        }
    }
}
