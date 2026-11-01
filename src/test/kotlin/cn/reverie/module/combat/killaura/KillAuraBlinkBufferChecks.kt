package cn.reverie.module.combat.killaura

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KillAuraBlinkBufferChecks {
    @Test
    fun packetResponsesAreNotLostWhileTheTickThreadFlushes() {
        val sent = mutableListOf<Int>()
        val blink = KillAuraBlinkBuffer<Int> { sent += it }
        val network = Executors.newSingleThreadExecutor()
        try {
            blink.blink()
            val producer = network.submit {
                repeat(1000) { assertTrue(blink.capture(it)) }
            }
            repeat(1000) { blink.flush() }
            producer.get(5, TimeUnit.SECONDS)
            blink.release(send = true)
            assertEquals((0 until 1000).toList(), sent)
        } finally {
            network.shutdownNow()
        }
    }

    @Test
    fun twoTickCycleKeepsReleaseBeforeTheNextAttackAndBlock() {
        val sent = mutableListOf("attack1", "block1")
        val blink = KillAuraBlinkBuffer<String> { sent += it }
        blink.blink()
        assertTrue(blink.capture("movement1"))
        blink.flush()
        assertEquals(listOf("attack1", "block1", "movement1"), sent)

        blink.capture("release1")
        blink.capture("movement2")
        blink.capture("attack2")
        blink.capture("block2")
        blink.flush()
        assertEquals(listOf("attack1", "block1", "movement1", "release1", "movement2", "attack2", "block2"), sent)
        assertTrue(blink.active)
    }

    @Test
    fun disableFlushesOnceAndStopsCapturingBeforeReplay() {
        val sent = mutableListOf<String>()
        lateinit var blink: KillAuraBlinkBuffer<String>
        blink = KillAuraBlinkBuffer { packet ->
            assertFalse(blink.capture("reentrant"))
            sent += packet
        }
        blink.blink()
        blink.capture("release")
        blink.capture("movement")
        blink.release(send = true)
        blink.release(send = true)
        assertEquals(listOf("release", "movement"), sent)
        assertFalse(blink.active)
        assertFalse(blink.capture("nextAttack"))
    }

    @Test
    fun worldChangeDiscardsPacketsInsteadOfReplayingIntoANewConnection() {
        val sent = mutableListOf<String>()
        val blink = KillAuraBlinkBuffer<String> { sent += it }
        blink.blink()
        blink.capture("oldMovement")
        blink.capture("oldAttack")
        blink.release(send = false)
        blink.blink()
        blink.capture("newMovement")
        blink.flush()
        assertEquals(listOf("newMovement"), sent)
    }
}
