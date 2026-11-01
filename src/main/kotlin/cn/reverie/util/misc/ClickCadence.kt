package cn.reverie.util.misc

/** Real-time click budget; at most [maxBurst] attempts are released per game tick. */
class ClickCadence {
    private var lastMs = -1L
    private var credit = 0.0

    fun reset(nowMs: Long = System.currentTimeMillis()) {
        lastMs = nowMs
        credit = 0.0
    }

    fun attempts(nowMs: Long, cps: Double, maxBurst: Int = 8): Int {
        if (lastMs < 0L) {
            reset(nowMs)
            return 0
        }
        val elapsed = (nowMs - lastMs).coerceIn(0L, 250L)
        lastMs = nowMs
        credit = (credit + elapsed * cps.coerceIn(1.0, 100.0) / 1000.0).coerceAtMost(maxBurst.toDouble())
        val count = (credit + 1e-9).toInt().coerceAtMost(maxBurst)
        credit = (credit - count).coerceAtLeast(0.0)
        return count
    }
}
