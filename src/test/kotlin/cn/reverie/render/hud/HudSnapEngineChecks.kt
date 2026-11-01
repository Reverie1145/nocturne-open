package cn.reverie.render.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HudSnapEngineChecks {
    @Test
    fun choosesTheClosestAlignmentFromTheOriginalDragPosition() {
        val moving = HudSnapEngine.Bounds(0f, 40f, 20f, 10f)
        val first = HudSnapEngine.Bounds(104f, 40f, 20f, 10f)
        val second = HudSnapEngine.Bounds(106f, 40f, 20f, 10f)

        val result = HudSnapEngine.resolve(
            moving = moving,
            desiredX = 100f,
            desiredY = 80f,
            screenWidth = 500f,
            screenHeight = 300f,
            others = listOf(first, second),
            snapDistance = 8f
        )

        assertEquals(104f, result.x)
        assertEquals(1, result.lines.count { it.isVertical })
        assertEquals(104f, result.lines.single { it.isVertical }.position)
    }

    @Test
    fun snapsAxesIndependentlyToTheScreenCenter() {
        val result = HudSnapEngine.resolve(
            moving = HudSnapEngine.Bounds(0f, 0f, 20f, 20f),
            desiredX = 243f,
            desiredY = 137f,
            screenWidth = 500f,
            screenHeight = 300f,
            others = emptyList(),
            snapDistance = 5f
        )

        assertEquals(240f, result.x)
        assertEquals(140f, result.y)
        assertEquals(2, result.lines.size)
        assertTrue(result.lines.all { it.isCenter })
    }

    @Test
    fun snapsToEvenSpacingAndShowsAGuideAcrossBothWidgets() {
        val other = HudSnapEngine.Bounds(50f, 20f, 20f, 20f)
        val result = HudSnapEngine.resolve(
            moving = HudSnapEngine.Bounds(0f, 40f, 10f, 10f),
            desiredX = 75f,
            desiredY = 100f,
            screenWidth = 500f,
            screenHeight = 300f,
            others = listOf(other),
            snapDistance = 3f
        )

        assertEquals(74f, result.x)
        val guide = result.singleVerticalGuide()
        assertEquals(74f, guide.position)
        assertEquals(20f, guide.start)
        assertEquals(110f, guide.end)
    }

    @Test
    fun keepsOversizedWidgetsInBoundsAndDoesNotEmitFalseGuides() {
        val result = HudSnapEngine.resolve(
            moving = HudSnapEngine.Bounds(0f, 0f, 140f, 20f),
            desiredX = 25f,
            desiredY = 30f,
            screenWidth = 100f,
            screenHeight = 100f,
            others = emptyList(),
            snapDistance = 8f
        )

        assertEquals(0f, result.x)
        assertEquals(30f, result.y)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun disablingMagneticAlignmentStillClampsAndClearsGuides() {
        val result = HudSnapEngine.resolve(
            moving = HudSnapEngine.Bounds(0f, 0f, 20f, 20f),
            desiredX = -10f,
            desiredY = 290f,
            screenWidth = 100f,
            screenHeight = 100f,
            others = emptyList(),
            snapDistance = 8f,
            enabled = false
        )

        assertEquals(0f, result.x)
        assertEquals(80f, result.y)
        assertFalse(result.lines.isNotEmpty())
    }

    private fun HudSnapEngine.Result.singleVerticalGuide(): SnapLine = lines.single { it.isVertical }
}
