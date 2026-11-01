package cn.reverie.render.hud

import cn.reverie.render.hud.impl.LyricHUDWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricHUDWidgetChecks {

    @Test
    fun defaultSettingsAndModesAreConfiguredCorrectly() {
        val widget = LyricHUDWidget()

        // Mode setting exists and includes 2D and 3D
        assertEquals("3D", widget.mode.value)
        assertTrue(widget.mode.modes.contains("2D"))
        assertTrue(widget.mode.modes.contains("3D"))

        // Default spawn distance in blocks
        assertEquals(4.5, widget.distance3D.value, 0.001)

        // 3D settings are visible when Mode is 3D
        assertTrue(widget.distance3D.visibility())
        assertTrue(widget.height3D.visibility())
        assertTrue(widget.lingerTime.visibility())
        assertTrue(widget.smoothSpeed.visibility())
        assertTrue(widget.spawnBlurAmount.visibility())
        assertTrue(widget.dynamicMotionBlur.visibility())
        assertTrue(widget.dynamicTilt.visibility())
        assertTrue(widget.floatingBreathing.visibility())
        assertTrue(widget.avoidObstacles.visibility())
        assertTrue(widget.depthSpacing3D.visibility())
        assertTrue(widget.seeThrough.visibility())
        assertTrue(widget.doubleSided.visibility())
        assertTrue(widget.sprintPacing.visibility())
        assertTrue(widget.textGlow.visibility())
        assertTrue(widget.textScale3D.visibility())

        // Switch to 2D
        widget.mode.value = "2D"
        assertEquals("2D", widget.mode.value)
        assertFalse(widget.distance3D.visibility())
        assertFalse(widget.height3D.visibility())
        assertFalse(widget.lingerTime.visibility())
        assertFalse(widget.smoothSpeed.visibility())
        assertFalse(widget.spawnBlurAmount.visibility())
        assertFalse(widget.dynamicMotionBlur.visibility())
        assertFalse(widget.dynamicTilt.visibility())
        assertFalse(widget.floatingBreathing.visibility())
        assertFalse(widget.avoidObstacles.visibility())
        assertFalse(widget.depthSpacing3D.visibility())
        assertFalse(widget.seeThrough.visibility())
        assertFalse(widget.doubleSided.visibility())
        assertFalse(widget.sprintPacing.visibility())
        assertFalse(widget.textGlow.visibility())
        assertFalse(widget.textScale3D.visibility())

        // Switch back to 3D
        widget.mode.value = "3D"
        assertTrue(widget.distance3D.visibility())
    }

    @Test
    fun settingsCanBeClampedAndModified() {
        val widget = LyricHUDWidget()

        widget.distance3D.setClamped(4.5)
        assertEquals(4.5, widget.distance3D.value, 0.001)

        widget.spawnBlurAmount.setClamped(8.0)
        assertEquals(8.0, widget.spawnBlurAmount.value, 0.001)

        widget.dynamicMotionBlur.value = false
        assertFalse(widget.dynamicMotionBlur.value)

        widget.dynamicTilt.value = false
        assertFalse(widget.dynamicTilt.value)

        widget.floatingBreathing.value = false
        assertFalse(widget.floatingBreathing.value)
    }
}
