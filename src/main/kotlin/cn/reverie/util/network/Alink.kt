package cn.reverie.util.network

import net.minecraft.network.protocol.Packet
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

class Alink {
    private val inbound = ConcurrentLinkedQueue<Packet<*>>()
    private val queuedAt = AtomicLong(-1L)

    @Volatile
    private var maxDelayMs = 50L

    fun setMaxDelayMs(value: Long) {
        maxDelayMs = maxOf(1L, value)
    }

    fun isEmpty(): Boolean = inbound.isEmpty()

    fun isActive(): Boolean = !inbound.isEmpty()

    fun getProgress(): Float {
        val start = queuedAt.get()
        if (start < 0L || inbound.isEmpty()) return 0f
        val progress = (System.currentTimeMillis() - start) / maxDelayMs.toFloat()
        if (progress < 0f) return 0f
        return minOf(progress, 1f)
    }

    fun queue(packet: Packet<*>?) {
        if (packet == null) return
        if (inbound.isEmpty()) queuedAt.compareAndSet(-1L, System.currentTimeMillis())
        inbound.add(packet)
    }

    fun release() {
        var packet = inbound.poll()
        while (packet != null) {
            PacketUtil.receiveSilent(packet)
            packet = inbound.poll()
        }
        if (inbound.isEmpty()) queuedAt.set(-1L)
    }

    fun clear() {
        inbound.clear()
        queuedAt.set(-1L)
    }
}
