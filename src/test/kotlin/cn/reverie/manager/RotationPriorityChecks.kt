package cn.reverie.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RotationPriorityChecks {
    @Test
    fun rotationSelectionPreservesFeaturePriorityAndShortCircuiting() {
        var laterPredicateCalled = false
        val selected = selectRotationOwner(
            scaffoldEnabled = false,
            killAura = { false },
            bowAim = { true },
            projectileAura = { laterPredicateCalled = true; true },
            bedBreaker = { laterPredicateCalled = true; true }
        )

        assertEquals(RotationOwner.BOW_AIM, selected)
        assertFalse(laterPredicateCalled, "lower-priority features must not be evaluated after a match")
    }

    @Test
    fun scaffoldAlwaysOwnsRotationWhileEnabled() {
        var featurePredicateCalled = false
        val selected = selectRotationOwner(
            scaffoldEnabled = true,
            killAura = { featurePredicateCalled = true; true },
            bowAim = { featurePredicateCalled = true; true },
            projectileAura = { featurePredicateCalled = true; true },
            bedBreaker = { featurePredicateCalled = true; true }
        )

        assertEquals(RotationOwner.SCAFFOLD, selected)
        assertFalse(featurePredicateCalled)
    }

    @Test
    fun autoMlgTakesPriorityOverKillAura() {
        var killAuraCalled = false
        val selected = selectRotationOwner(
            scaffoldEnabled = false,
            killAura = { killAuraCalled = true; true },
            bowAim = { false },
            projectileAura = { false },
            bedBreaker = { false },
            autoMlg = { true }
        )

        assertEquals(RotationOwner.AUTO_MLG, selected)
        assertFalse(killAuraCalled, "killAura should not be evaluated when autoMlg is active")
    }
}
