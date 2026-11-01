package cn.reverie.module.combat.killaura

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KillAuraWatchdogCycleChecks {
    @Test
    fun sourceStartsAtMinusOneAndDoesNotAttackAtZero() {
        val driver = Driver()
        assertEquals(-1, driver.cycle.blockTicks)
        driver.pre()
        assertEquals(0, driver.cycle.blockTicks)
        assertEquals(listOf("sprintKey:false", "raycast"), driver.calls)
        assertFalse(driver.cycle.allowAttack)
        assertFalse(driver.cycle.blocking)
    }

    @Test
    fun sourceOrderIsAttackBlockAcquireBlinkThenFlushRelease() {
        val driver = Driver()
        driver.pre()
        driver.cycle.onPostTick()
        driver.calls.clear()

        driver.pre()
        assertEquals(listOf("sprintKey:false", "raycast", "attack:true", "block", "acquire", "blink"), driver.calls)
        assertEquals(1, driver.cycle.blockTicks)
        assertTrue(driver.cycle.blocking)
        driver.cycle.onPostTick()
        driver.calls.clear()

        driver.pre()
        assertEquals(listOf("sprintKey:false", "raycast", "flush", "stopUsing", "releaseUsing"), driver.calls)
        assertEquals(2, driver.cycle.blockTicks)
        driver.cycle.onPostTick()
        assertEquals(0, driver.cycle.blockTicks)
        driver.calls.clear()

        driver.pre()
        assertEquals(listOf("sprintKey:false", "raycast", "attack:true", "block", "flush", "blink"), driver.calls)
    }

    @Test
    fun bothLocalAndServerSprintFreezeTheCounterBeforeRaycast() {
        for ((local, server) in listOf(true to false, false to true)) {
            val driver = Driver()
            driver.pre(sprinting = local, serverSprinting = server)
            assertEquals(-1, driver.cycle.blockTicks)
            assertEquals(listOf("sprintKey:false", "stopSprinting", "sprintKey:false"), driver.calls)
        }
    }

    @Test
    fun missingSwordDoesNotDisableAnUnconditionalAutoblockSession() {
        val driver = Driver()
        driver.pre(holdingSword = false)
        assertTrue(driver.cycle.rightMouseABActive)
        assertFalse(driver.cycle.allowAttack)
        assertEquals(-1, driver.cycle.blockTicks)
        assertEquals(listOf("sprintKey:false"), driver.calls)
    }

    @Test
    fun onlyPressedActivationAndReleaseKeepTheSourceCounterRules() {
        val driver = Driver(onlyPressed = true)
        driver.pre(mouseDown = false)
        assertFalse(driver.cycle.rightMouseABActive)
        assertTrue(driver.cycle.allowAttack)
        driver.pre(mouseDown = true)
        assertEquals(0, driver.cycle.blockTicks)
        driver.pre(mouseDown = true)
        driver.calls.clear()
        driver.pre(mouseDown = false)
        assertEquals(listOf("stopUsing", "releaseUsing", "release:true"), driver.calls)
        assertFalse(driver.cycle.rightMouseABActive)
        assertEquals(0, driver.cycle.blockTicks)
        assertTrue(driver.cycle.allowAttack)
    }

    @Test
    fun releasingOnlyPressedWaitsForMovementBeforeOrdinaryAttack() {
        val driver = Driver(onlyPressed = true)
        driver.pre(mouseDown = true)
        driver.pre(mouseDown = true)
        driver.calls.clear()
        driver.pre(mouseDown = false)
        assertTrue(driver.cycle.allowAttack, "The source resumes ordinary attack on deactivation")
        assertFalse(driver.attackBoundary.canAttackOrUse,
            "Its release packet still forbids an immediate attack under PacketOrderI")
        // A tick callback or Blink flush alone cannot clear this condition.
        driver.cycle.onPostTick()
        assertFalse(driver.attackBoundary.canAttackOrUse)
        driver.attackBoundary.onMovement()
        assertTrue(driver.attackBoundary.canAttackOrUse)
    }

    @Test
    fun disableReleasesItemBeforeFlushingTheBlinkSession() {
        val driver = Driver()
        driver.pre()
        driver.pre()
        driver.calls.clear()
        driver.cycle.onDisable()
        assertEquals(listOf("stopUsing", "releaseUsing", "release:true", "render:false"), driver.calls)
        assertEquals(0, driver.cycle.blockTicks)
        assertFalse(driver.cycle.blocking)
    }

    @Test
    fun losingTargetUsesTickCleanupThenMotionCleanupWithoutResettingCounter() {
        val driver = Driver()
        driver.pre()
        driver.pre()
        driver.calls.clear()
        driver.pre(hasTarget = false)
        assertEquals(listOf("stopUsing", "releaseUsing", "flush", "release:false"), driver.calls)
        assertEquals(1, driver.cycle.blockTicks)
        driver.calls.clear()
        driver.cycle.onPreMotion(hasTarget = false)
        assertEquals(listOf("render:false", "release:true"), driver.calls)
        assertEquals(1, driver.cycle.blockTicks)
        assertTrue(driver.cycle.rightMouseABActive)
    }

    @Test
    fun motionOnlyChangesThePoseAndDoesNotAdvanceTheCycle() {
        val driver = Driver()
        driver.cycle.onPreMotion(hasTarget = true)
        assertEquals(listOf("render:false", "render:true"), driver.calls)
        assertEquals(-1, driver.cycle.blockTicks)
    }

    @Test
    fun existingLocalSwordBlockSkipsTheExtraUsePacket() {
        val driver = Driver()
        driver.pre()
        driver.calls.clear()
        driver.playerBlocking = true
        driver.pre()
        assertEquals(listOf("sprintKey:false", "raycast", "attack:true", "acquire", "blink"), driver.calls)
    }

    private class Driver(private val onlyPressed: Boolean = false) : KillAuraWatchdogCycle.Actions {
        val calls = mutableListOf<String>()
        var playerBlocking = false
        var moduleBlocking = false
        val attackBoundary = KillAuraAttackBoundary()
        val cycle = KillAuraWatchdogCycle(this).also { it.onEnable(onlyPressed) }

        fun pre(
            mouseDown: Boolean = false,
            holdingSword: Boolean = true,
            hasTarget: Boolean = true,
            sprinting: Boolean = false,
            serverSprinting: Boolean = false
        ) = cycle.onPreTick(onlyPressed, mouseDown, holdingSword, hasTarget, true, sprinting, serverSprinting)

        override fun disableSprintKey() { calls += "sprintKey:false" }
        override fun stopSprinting() { calls += "stopSprinting" }
        override fun raycast(): Boolean { calls += "raycast"; return true }
        override fun attack(raycastHit: Boolean) {
            assertTrue(moduleBlocking, "Aura.java sets blocking before doAttack")
            calls += "attack:$raycastHit"
        }
        override fun playerIsBlocking(): Boolean = playerBlocking
        override fun realBlock() { calls += "block"; playerBlocking = true }
        override fun stopUsingItem() { calls += "stopUsing"; playerBlocking = false }
        override fun releaseUsingItem() { calls += "releaseUsing"; attackBoundary.onRelease() }
        override fun setBlocking(blocking: Boolean) { moduleBlocking = blocking }
        override fun setRenderBlock(blocking: Boolean) { calls += "render:$blocking" }
        override fun acquireBlink(): KillAuraWatchdogCycle.BlinkSession {
            calls += "acquire"
            return object : KillAuraWatchdogCycle.BlinkSession {
                override fun flush() { calls += "flush" }
                override fun blink() { calls += "blink" }
                override fun release(send: Boolean) { calls += "release:$send" }
            }
        }
    }
}
