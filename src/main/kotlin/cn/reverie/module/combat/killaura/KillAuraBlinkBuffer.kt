package cn.reverie.module.combat.killaura

/**
 * FIFO replay follows Fuliki's BlinkUtils: flushing keeps the session active,
 * and capture/flush/release share one lock, including network-thread responses.
 * Aura.java's original BlinkUtility implementation was not supplied.
 */
internal class KillAuraBlinkBuffer<T>(private val replay: (T) -> Unit) : KillAuraWatchdogCycle.BlinkSession {
    private val packets = ArrayDeque<T>()
    @Volatile var active = false
        private set

    @Synchronized
    override fun blink() {
        active = true
    }

    @Synchronized
    fun capture(packet: T): Boolean {
        if (!active) return false
        packets.addLast(packet)
        return true
    }

    @Synchronized
    override fun flush() {
        while (packets.isNotEmpty()) replay(packets.removeFirst())
    }

    @Synchronized
    override fun release(send: Boolean) {
        active = false
        if (send) flush() else packets.clear()
    }
}
