package cn.reverie.module.combat.backtrack

import cn.reverie.util.network.PacketUtil
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

class BacktrackPacketQueue {
    data class HeldPacket(
        val packet: Packet<*>,
        val receiveTime: Long,
        val relatedEntityIds: List<Int>,
        val positionUpdate: Boolean
    ) {
        fun age(now: Long): Long = now - receiveTime
    }

    private val queue = ConcurrentLinkedDeque<HeldPacket>()
    private val flushing = AtomicBoolean(false)

    fun isFlushing(): Boolean = flushing.get()
    fun isEmpty(): Boolean = queue.isEmpty()
    fun size(): Int = queue.size
    fun peek(): HeldPacket? = queue.peekFirst()
    fun peekLast(): HeldPacket? = queue.peekLast()

    fun enqueue(packet: Packet<*>, receiveTime: Long, relatedEntityIds: List<Int>, positionUpdate: Boolean) {
        queue.addLast(HeldPacket(packet, receiveTime, relatedEntityIds.toList(), positionUpdate))
    }

    fun oldestAge(now: Long): Long = queue.peekFirst()?.age(now) ?: 0L

    fun releaseExpired(now: Long, maxDelayMs: Long): Int {
        var released = 0
        while (true) {
            val first = queue.peekFirst() ?: break
            if (first.age(now) < maxDelayMs || !pollAndApply()) break
            released++
        }
        return released
    }

    fun releaseUntilIndex(exclusiveIndex: Int): Int {
        var released = 0
        while (released < exclusiveIndex && pollAndApply()) released++
        return released
    }

    fun flushNow() {
        val mc = Minecraft.getInstance()
        if (!mc.isSameThread) {
            mc.execute { flushNow() }
            return
        }
        while (queue.isNotEmpty()) {
            if (!pollAndApply()) break
        }
    }

    fun applyNow(packet: Packet<*>) {
        val mc = Minecraft.getInstance()
        if (!mc.isSameThread) {
            mc.execute { applyNow(packet) }
            return
        }
        flushing.set(true)
        try {
            PacketUtil.receiveSilent(packet)
        } finally {
            flushing.set(false)
        }
    }

    fun dropAll() = queue.clear()

    fun snapshot(): List<HeldPacket> = ArrayList(queue)

    fun containsEntity(entityId: Int): Boolean = queue.any { entityId in it.relatedEntityIds }

    private fun pollAndApply(): Boolean {
        val held = queue.pollFirst() ?: return false
        if (apply(held)) return true
        queue.addFirst(held)
        return false
    }

    private fun apply(held: HeldPacket): Boolean {
        val mc = Minecraft.getInstance()
        if (!mc.isSameThread) {
            mc.execute { flushNow() }
            return false
        }
        flushing.set(true)
        return try {
            PacketUtil.receiveSilent(held.packet)
            true
        } catch (_: Throwable) {
            false
        } finally {
            flushing.set(false)
        }
    }
}
