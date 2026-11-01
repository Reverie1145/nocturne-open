package cn.reverie.module.misc

import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.module.Category
import cn.reverie.module.ModeSetting
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import cn.reverie.render.ui.notification.NotificationManager

/**
 * Client-side guard for unusually dense groups of inbound packets.
 * This module never creates or sends packets; it can only observe, cancel, or
 * locally disconnect from an inbound burst before vanilla handles the packet.
 */
object Crash : Module(
    name = "Crash",
    description = "Monitor and limit suspicious inbound packet bursts",
    category = Category.MISC
) {
    private const val MODE_BALANCED = "Balanced"
    private const val MODE_ALL = "All Guarded"

    private val families = listOf(
        "Entity", "Chunk", "Particle", "Sound", "Chat", "Inventory",
        "Metadata", "Scoreboard", "Custom Payload", "Boss Bar", "Map Data",
        "Light", "Explosion", "Advancements", "Recipes", "Player List", "World Events"
    )
    private val allFamilies = families.toSet()

    val mode = ModeSetting(
        "Mode",
        "Inbound packet family to monitor",
        MODE_BALANCED,
        listOf(MODE_BALANCED, MODE_ALL) + families
    )

    val action = ModeSetting(
        "Action",
        "How to handle packets after the selected family exceeds its limit",
        "Monitor",
        listOf("Monitor", "Cancel Excess", "Cancel & Disconnect")
    )

    val packetLimit = NumberSetting(
        "Packet Limit",
        "Allowed packets from one selected family per time window",
        120.0,
        5.0,
        2000.0,
        5.0
    )

    val window = NumberSetting(
        "Window (ms)",
        "Length of the packet counting window",
        1000.0,
        100.0,
        10000.0,
        100.0
    )

    private val counters = ConcurrentHashMap<String, BurstCounter>()
    private val disconnectQueued = AtomicBoolean(false)

    init {
        listen(PacketEvent::class.java) { onPacket(it) }
        mode.onChanged = { resetState() }
        window.onChanged = { resetState() }
    }

    override val suffix: String
        get() = "${mode.value} · ${action.value}"

    override fun onEnable() = resetState()

    override fun onDisable() = resetState()

    private fun onPacket(event: PacketEvent) {
        if (!enabled || !event.isReceive) return

        val packetFamilies = classify(event.packet)
        if (packetFamilies.isEmpty()) return

        val selected = selectedFamilies()
        val matching = packetFamilies.filter(selected::contains)
        if (matching.isEmpty()) return

        val now = System.nanoTime()
        val windowNanos = window.getInt().coerceAtLeast(100).toLong() * NANOS_PER_MILLI
        val limit = packetLimit.getInt().coerceAtLeast(1)
        for (family in matching) {
            val count = counters.computeIfAbsent(family) { BurstCounter() }.increment(now, windowNanos)
            if (count <= limit) continue

            when (action.value) {
                "Cancel Excess" -> event.cancel()
                "Cancel & Disconnect" -> {
                    event.cancel()
                    queueDisconnect(family, count)
                }
            }

            if (count == limit + 1) {
                queueNotice(family, count, limit)
            }
            if (event.isCancelled) return
        }
    }

    private fun selectedFamilies(): Set<String> = when (mode.value) {
        MODE_ALL -> allFamilies
        MODE_BALANCED -> BALANCED_FAMILIES
        else -> setOf(mode.value)
    }

    private fun queueNotice(family: String, count: Int, limit: Int) {
        Minecraft.getInstance().execute {
            if (enabled) {
                NotificationManager.warning("Crash Guard", "$family burst: over $limit packets (${count}+) in ${window.getInt()} ms")
            }
        }
    }

    private fun queueDisconnect(family: String, count: Int) {
        if (!disconnectQueued.compareAndSet(false, true)) return
        Minecraft.getInstance().execute {
            if (!enabled) {
                disconnectQueued.set(false)
                return@execute
            }
            val connection = Minecraft.getInstance().connection?.connection
            if (connection == null) {
                disconnectQueued.set(false)
            } else {
                connection.disconnect(Component.literal("Crash Guard: inbound $family burst ($count packets)"))
            }
        }
    }

    private fun resetState() {
        counters.clear()
        disconnectQueued.set(false)
    }

    private fun classify(packet: Packet<*>): Set<String> {
        val name = packet.javaClass.simpleName.lowercase(Locale.ROOT)
        if (!name.startsWith("clientbound")) return emptySet()

        val family = when {
            any(name, "addentity", "addplayer", "addmob", "addexperienceorb") -> "Entity"
            any(name, "levelchunk", "forgetlevelchunk") -> "Chunk"
            any(name, "levelparticles", "particle") -> "Particle"
            any(name, "sound") -> "Sound"
            any(name, "systemchat", "playerchat", "disguisedchat") -> "Chat"
            any(name, "containersetcontent", "containersetslot", "openscreen") -> "Inventory"
            any(name, "setentitydata", "setequipment", "updateattributes") -> "Metadata"
            any(name, "setobjective", "setscore", "displayobjective", "playerteam") -> "Scoreboard"
            any(name, "custompayload") -> "Custom Payload"
            any(name, "bossevent") -> "Boss Bar"
            any(name, "mapitemdata") -> "Map Data"
            any(name, "lightupdate") -> "Light"
            any(name, "explode", "explosion") -> "Explosion"
            any(name, "updateadvancements") -> "Advancements"
            any(name, "updaterecipes") -> "Recipes"
            any(name, "playerinfoupdate", "playerinforemove") -> "Player List"
            any(name, "levelevent", "gameevent", "blockupdate", "sectionblocksupdate") -> "World Events"
            else -> null
        } ?: return emptySet()

        return setOf(family)
    }

    private fun any(value: String, vararg parts: String): Boolean = parts.any { value.contains(it) }

    private class BurstCounter {
        private var windowStart = 0L
        private var count = 0

        @Synchronized
        fun increment(now: Long, duration: Long): Int {
            if (windowStart == 0L || now - windowStart >= duration || now < windowStart) {
                windowStart = now
                count = 0
            }
            count++
            return count
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L

    private val BALANCED_FAMILIES = setOf(
        "Entity", "Chunk", "Particle", "Custom Payload", "Metadata", "Player List"
    )
}
