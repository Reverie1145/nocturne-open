package cn.reverie.module.player.bedbreaker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BedTeamProtectionChecks {
    @Test
    fun allStandardPalettesIdentifyExactlyOneOwnColorAmongAllSixteenBeds() {
        for ((ownColor, rgbs) in PALETTE) {
            for (rgb in rgbs) {
                val guard = settledGuard(ownColor, rgb)
                assertEquals(rgb and 0xFFFFFF, guard.helmetRgb)
                assertTrue(guard.identification!!.exact)
                assertEquals(ownColor, guard.identification!!.color)
                assertEquals(0.0, guard.identification!!.candidates.first().distance)
                for (bedColor in PALETTE.keys) {
                    assertEquals(bedColor == ownColor, guard.isProtectedBed(bedColor),
                        "$ownColor #${rgb.toString(16)} versus $bedColor")
                }
            }
        }
    }

    @Test
    fun aliasesOfTheSameTeamDoNotInterruptEnemyMining() {
        for ((color, rgbs) in PALETTE) {
            val guard = settledGuard(color)
            for (rgb in rgbs) {
                guard.observeHelmetColor(rgb)
                assertTrue(guard.readyToMine, color)
                assertEquals(color, guard.protectedColor)
            }
        }
    }

    @Test
    fun alphaDoesNotChangeHelmetColor() {
        for ((color, rgbs) in PALETTE) {
            val guard = settledGuard(color, rgbs.first() or (0xFF shl 24))
            assertEquals(color, guard.protectedColor)
        }
    }

    @Test
    fun ownBedCannotBeMinedDirectlyOrAsAnEnemyBedsBlocker() {
        val guard = settledGuard("RED")
        assertFalse(guard.canMine("RED", "RED"))
        assertFalse(guard.canMine("RED", null))
        assertFalse(guard.canMine("BLUE", "RED"))
        assertTrue(guard.canMine("BLUE", "BLUE"))
        assertTrue(guard.canMine("BLUE", null))
    }

    @Test
    fun missingHelmetStopsMiningUntilItsColorReturns() {
        val guard = settledGuard("RED")
        guard.observeHelmetColor(null)
        assertEquals("RED", guard.protectedColor)
        assertFalse(guard.readyToMine)
        for (bedColor in PALETTE.keys) {
            assertTrue(guard.isProtectedBed(bedColor))
            assertFalse(guard.canMine(bedColor, null))
        }
        guard.observeHelmetColor(PALETTE.getValue("RED").last())
        assertTrue(guard.readyToMine)
        assertFalse(guard.canMine("RED", null))
        assertTrue(guard.canMine("BLUE", null))
    }

    @Test
    fun customTintAlwaysIdentifiesOneClosestTeamAndExposesItsInference() {
        val guard = BedTeamProtection(mapOf("BLACK" to listOf(0x000000), "WHITE" to listOf(0xFFFFFF)))
        repeat(3) { guard.observeHelmetColor(0x808080) }
        assertEquals("WHITE", guard.protectedColor)
        assertTrue(guard.readyToMine)
        assertFalse(guard.identification!!.exact)
        assertEquals("WHITE", guard.identification!!.candidates.first().color)
        assertTrue(guard.identification!!.candidates.first().distance > 12.0)
        assertFalse(guard.isProtectedBed("BLACK"))
        assertTrue(guard.isProtectedBed("WHITE"))
        assertFalse(guard.canMine("WHITE", null))
    }

    @Test
    fun closeColorCandidatesStillProduceOneResultAfterStableConfirmation() {
        val guard = BedTeamProtection(mapOf("A" to listOf(0x7F7F7F), "B" to listOf(0x818181)))
        repeat(3) { guard.observeHelmetColor(0x7F7F7F) }
        assertTrue(guard.readyToMine)
        guard.observeHelmetColor(0x808080)
        assertEquals("A", guard.protectedColor)
        assertFalse(guard.readyToMine)
        assertFalse(guard.canMine("B", null))
        assertEquals(2, guard.identification!!.candidates.size)
        assertFalse(guard.identification!!.exact)
        repeat(2) { guard.observeHelmetColor(0x808080) }
        assertTrue(guard.readyToMine)
        assertEquals(guard.identification!!.color, guard.protectedColor)
        assertEquals(1, listOf("A", "B").count { guard.isProtectedBed(it) })
    }

    @Test
    fun everyReadableCustomTintSelectsExactlyOneOfTheSixteenBedColors() {
        for (rgb in listOf(0x55FF55, 0x55FFFF, 0xFF55FF, 0x112233, 0x987654, 0x808080)) {
            val guard = BedTeamProtection(PALETTE)
            repeat(3) { guard.observeHelmetColor(rgb) }
            assertTrue(guard.readyToMine)
            assertEquals(1, PALETTE.keys.count { guard.isProtectedBed(it) })
            assertEquals(guard.protectedColor, guard.identification!!.color)
            assertEquals(3, guard.identification!!.candidates.size)
        }
    }

    @Test
    fun changedTeamStopsMiningUntilThreeConsistentObservations() {
        val guard = settledGuard("RED")
        val blue = PALETTE.getValue("BLUE").first()
        repeat(2) {
            guard.observeHelmetColor(blue)
            assertEquals("RED", guard.protectedColor)
            assertFalse(guard.readyToMine)
            assertFalse(guard.canMine("BLUE", null))
        }
        guard.observeHelmetColor(blue)
        assertEquals("BLUE", guard.protectedColor)
        assertTrue(guard.readyToMine)
        assertFalse(guard.canMine("BLUE", null))
        assertTrue(guard.canMine("RED", null))
    }

    @Test
    fun transientHelmetColorDoesNotReassignTheOwnBed() {
        val guard = settledGuard("RED")
        guard.observeHelmetColor(PALETTE.getValue("BLUE").first())
        assertFalse(guard.readyToMine)
        guard.observeHelmetColor(PALETTE.getValue("RED").first())
        assertTrue(guard.readyToMine)
        assertEquals("RED", guard.protectedColor)
        assertFalse(guard.canMine("RED", null))
    }

    @Test
    fun resetRequiresFreshTeamIdentification() {
        val guard = settledGuard("RED")
        guard.reset()
        assertNull(guard.protectedColor)
        assertNull(guard.helmetRgb)
        assertNull(guard.identification)
        assertFalse(guard.readyToMine)
        assertFalse(guard.canMine("BLUE", null))
        guard.observeHelmetColor(null)
        repeat(3) { guard.observeHelmetColor(PALETTE.getValue("BLUE").first()) }
        assertEquals("BLUE", guard.protectedColor)
        assertTrue(guard.readyToMine)
    }

    @Test
    fun smallCustomShadeDifferencesStillIdentifyTheirTeam() {
        assertEquals("RED", settledGuard("RED", 0xFE0000).protectedColor)
        assertEquals("CYAN", settledGuard("CYAN", 0x297698).protectedColor)
    }

    @Test
    fun disappearanceOfTheSourceBedStopsItsTrackedBlocker() {
        val guard = settledGuard("RED")
        assertFalse(guard.canMine(null, null))
        assertFalse(guard.canMine(null, "BLUE"))
    }

    private fun settledGuard(color: String, rgb: Int = PALETTE.getValue(color).first()): BedTeamProtection<String> {
        val guard = BedTeamProtection(PALETTE)
        repeat(3) { guard.observeHelmetColor(rgb) }
        assertTrue(guard.readyToMine, "$color #${rgb.toString(16)}")
        assertEquals(color, guard.protectedColor)
        return guard
    }

    private companion object {
        // Independent fixtures for Minecraft's texture, firework and named text palettes.
        val PALETTE = linkedMapOf(
            "WHITE" to listOf(0xF9FFFE, 0xF0F0F0, 0xFFFFFF),
            "ORANGE" to listOf(0xF9801D, 0xEB8844, 0xFF681F),
            "MAGENTA" to listOf(0xC74EBD, 0xC354CD, 0xFF00FF),
            "LIGHT_BLUE" to listOf(0x3AB3DA, 0x6689D3, 0x9AC0CD),
            "YELLOW" to listOf(0xFED83D, 0xDECF2A, 0xFFFF00),
            "LIME" to listOf(0x80C71F, 0x41CD34, 0xBFFF00),
            "PINK" to listOf(0xF38BAA, 0xD88198, 0xFF69B4),
            "GRAY" to listOf(0x474F52, 0x434343, 0x808080),
            "LIGHT_GRAY" to listOf(0x9D9D97, 0xABABAB, 0xD3D3D3),
            "CYAN" to listOf(0x169C9C, 0x287697, 0x00FFFF),
            "PURPLE" to listOf(0x8932B8, 0x7B2FBE, 0xA020F0),
            "BLUE" to listOf(0x3C44AA, 0x253192, 0x0000FF),
            "BROWN" to listOf(0x835432, 0x51301A, 0x8B4513),
            "GREEN" to listOf(0x5E7C16, 0x3B511A, 0x00FF00),
            "RED" to listOf(0xB02E26, 0xB3312C, 0xFF0000),
            "BLACK" to listOf(0x1D1D21, 0x1E1B1B, 0x000000),
        )
    }
}

