package cn.reverie.util.misc

class TimerUtil {
    @JvmField
    var time: Long = System.currentTimeMillis()

    init {
        reset()
    }

    fun reset() {
        time = System.currentTimeMillis()
    }

    fun finished(delay: Long): Boolean = System.currentTimeMillis() - delay >= time

    fun hasTimeElapsed(time: Long): Boolean = System.currentTimeMillis() - this.time > time

    fun hasTimeElapsed(time: Double): Boolean = hasTimeElapsed(time.toLong())

    fun getTime(): Long = System.currentTimeMillis() - time
}
