package cn.reverie.module.player

import cn.reverie.module.Category
import cn.reverie.util.rotation.MoveFix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoMLGChecks {
    @Test
    fun moduleMetadataAndSettings() {
        assertEquals("AutoMLG", AutoMLG.name)
        assertEquals(Category.PLAYER, AutoMLG.category)
        assertEquals(2.0, AutoMLG.fallDistance.value)
        assertEquals(4.5, AutoMLG.reach.value)
        assertTrue(AutoMLG.autoPickup.value)
        assertTrue(AutoMLG.returnSlot.value)
        assertTrue(AutoMLG.visualRotation.value)
        assertEquals("Silent", AutoMLG.moveFix.value)
        assertEquals(MoveFix.Silent, AutoMLG.getMoveFix())
        assertEquals(AutoMLG.moveFix, AutoMLG.movementFixMode)
        assertFalse(AutoMLG.autoStuck.value)
        assertEquals("CancelMove", AutoMLG.autoStuckMode.value)
    }

    @Test
    fun moveFixSelection() {
        AutoMLG.moveFix.value = "None"
        assertEquals(MoveFix.None, AutoMLG.getMoveFix())
        AutoMLG.moveFix.value = "Strict"
        assertEquals(MoveFix.Strict, AutoMLG.getMoveFix())
        AutoMLG.moveFix.value = "Silent"
        assertEquals(MoveFix.Silent, AutoMLG.getMoveFix())
    }

    @Test
    fun suffixReflectsAutoPickup() {
        AutoMLG.autoPickup.value = true
        assertEquals("Water + Pickup", AutoMLG.suffix)
        AutoMLG.autoPickup.value = false
        assertEquals("Water", AutoMLG.suffix)
        AutoMLG.autoPickup.value = true
    }

    @Test
    fun enableAndDisableLifecycle() {
        AutoMLG.enabled = true
        assertTrue(AutoMLG.enabled)
        AutoMLG.enabled = false
        assertFalse(AutoMLG.enabled)
    }
}
