package cn.reverie.module.combat.killaura

/**
 * Aura.java's state transitions. Tick PRE/POST drive the cycle; motion PRE
 * only updates the fake pose and releases a blink when there is no target.
 */
internal class KillAuraWatchdogCycle(private val actions: Actions) {
    interface BlinkSession {
        fun flush()
        fun blink()
        fun release(send: Boolean)
    }

    interface Actions {
        fun disableSprintKey()
        fun stopSprinting()
        fun raycast(): Boolean
        fun attack(raycastHit: Boolean)
        fun playerIsBlocking(): Boolean
        fun realBlock()
        fun stopUsingItem()
        fun releaseUsingItem()
        fun setBlocking(blocking: Boolean)
        fun setRenderBlock(blocking: Boolean)
        fun acquireBlink(): BlinkSession
    }

    var rightMouseABActive = false
        private set
    var blockTicks = -1
        private set
    var blocking = false
        private set
    var allowAttack = true
        private set
    private var lagBlinkSession: BlinkSession? = null

    fun onEnable(onlyPressed: Boolean) {
        rightMouseABActive = !onlyPressed
    }

    fun onDisable() {
        if (rightMouseABActive) abDisabled()
        rightMouseABActive = false
        actions.setRenderBlock(false)
    }

    fun onPreTick(
        onlyPressed: Boolean,
        rightMouseDown: Boolean,
        holdingSword: Boolean,
        hasTarget: Boolean,
        playerAlive: Boolean,
        sprinting: Boolean,
        serverSprinting: Boolean
    ) {
        if (onlyPressed && rightMouseDown && holdingSword && !rightMouseABActive) {
            rightMouseABActive = true
        }
        if (onlyPressed && rightMouseABActive && (!rightMouseDown || !holdingSword)) {
            abDisabled()
            rightMouseABActive = false
        }
        if (!hasTarget) {
            newReleaseBlock()
            lagBlinkSession?.flush()
            lagBlinkSession?.release(false)
            return
        }
        allowAttack = true
        if (!playerAlive && blocking) newReleaseBlock()
        if (!rightMouseABActive) return

        allowAttack = false
        actions.disableSprintKey()
        if (!holdingSword) return
        if (serverSprinting || sprinting) {
            actions.stopSprinting()
            actions.disableSprintKey()
            return
        }

        blockTicks++
        val raycastHit = actions.raycast()
        when (blockTicks) {
            1 -> {
                setBlocking(true)
                actions.attack(raycastHit)
                if (!actions.playerIsBlocking()) actions.realBlock()
                if (lagBlinkSession == null) {
                    lagBlinkSession = actions.acquireBlink()
                } else {
                    lagBlinkSession?.flush()
                }
                lagBlinkSession?.blink()
            }
            2 -> {
                lagBlinkSession?.flush()
                newReleaseBlock()
            }
            3 -> lagBlinkSession?.flush()
        }
    }

    fun onPostTick() {
        if (!rightMouseABActive) return
        actions.raycast()
        if (blockTicks == 2) blockTicks = 0
    }

    fun onPreMotion(hasTarget: Boolean) {
        actions.setRenderBlock(false)
        if (!hasTarget) {
            newReleaseBlock()
            lagBlinkSession?.release(true)
            lagBlinkSession = null
            return
        }
        if (rightMouseABActive) actions.setRenderBlock(true)
    }

    private fun setBlocking(value: Boolean) {
        blocking = value
        actions.setBlocking(value)
    }

    private fun newReleaseBlock() {
        if (blocking) {
            actions.stopUsingItem()
            actions.releaseUsingItem()
        }
        setBlocking(false)
    }

    private fun abDisabled() {
        newReleaseBlock()
        lagBlinkSession?.release(true)
        lagBlinkSession = null
        blockTicks = 0
    }

    /** World replacement has no corresponding connection to replay into. */
    fun discard() {
        if (blocking) actions.stopUsingItem()
        setBlocking(false)
        lagBlinkSession?.release(false)
        lagBlinkSession = null
        blockTicks = 0
        rightMouseABActive = false
        actions.setRenderBlock(false)
    }
}
