package cn.reverie.module.player.bedbreaker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedTeamChatLogChecks {
    @Test
    fun samplesAtMostOncePerSecond() {
        val log = BedTeamChatLog()
        assertTrue(log.shouldSample(0))
        assertFalse(log.shouldSample(0))
        assertFalse(log.shouldSample(999))
        assertTrue(log.shouldSample(1000))
    }

    @Test
    fun stationaryIdentityAndBedsAreLoggedOnlyOnce() {
        val log = BedTeamChatLog()
        val beds = mapOf("home" to "Red @home: own", "enemy" to "Blue @enemy: enemy")
        val expected = listOf("Helmet #FF0000 -> RED", "1 own, 1 enemy", "Red @home: own", "Blue @enemy: enemy")
        assertEquals(expected, log.changes("RED", expected.take(1), expected[1], beds))
        assertTrue(log.changes("RED", expected.take(1), expected[1], beds).isEmpty())
    }

    @Test
    fun newBedLogsItsVerdictWithoutRepeatingTheHelmetIdentity() {
        val log = BedTeamChatLog()
        log.changes("RED", listOf("Helmet -> RED"), "1 own", mapOf("home" to "Home protected"))
        val messages = log.changes("RED", listOf("Helmet -> RED"), "1 own, 1 enemy",
            mapOf("home" to "Home protected", "enemy" to "New enemy"))
        assertEquals(listOf("1 own, 1 enemy", "New enemy"), messages)
    }

    @Test
    fun changedIdentityReprintsBedVerdicts() {
        val log = BedTeamChatLog()
        log.changes("RED", listOf("Helmet -> RED"), "1 own", mapOf("bed" to "Red protected"))
        assertEquals(listOf("Helmet -> BLUE", "0 own", "Red enemy"),
            log.changes("BLUE", listOf("Helmet -> BLUE"), "0 own", mapOf("bed" to "Red enemy")))
    }

    @Test
    fun bedLeavingRangeUpdatesTheSummary() {
        val log = BedTeamChatLog()
        log.changes("RED", listOf("Helmet -> RED"), "1 own", mapOf("home" to "Home protected"))
        assertEquals(listOf("0 own in range"), log.changes("RED", listOf("Helmet -> RED"), "0 own in range", emptyMap()))
    }

    @Test
    fun worldResetAllowsTheSameResultToBeShownAgain() {
        val log = BedTeamChatLog()
        assertTrue(log.shouldSample(0))
        log.changes("RED", listOf("Helmet -> RED"), "0 beds", emptyMap())
        log.reset()
        assertTrue(log.shouldSample(0))
        assertEquals(listOf("Helmet -> RED", "0 beds"), log.changes("RED", listOf("Helmet -> RED"), "0 beds", emptyMap()))
    }
}
