package cn.reverie.module.misc

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.util.misc.ChatUtil
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

object BanTracker : Module(
    name = "BanTracker",
    description = "Reports Hypixel Watchdog and staff ban increases in chat",
    category = Category.MISC,
    keyCode = GLFW.GLFW_KEY_UNKNOWN
) {
    private val lock = Any()
    @Volatile private var scheduler: ScheduledExecutorService? = null
    @Volatile private var generation = 0L
    private var previousWatchdog = 0
    private var previousStaff = 0
    private var hasBaseline = false

    override fun onEnable() {
        stopScheduler()
        synchronized(lock) {
            previousWatchdog = 0
            previousStaff = 0
            hasBaseline = false
        }
        val currentGeneration = ++generation
        val factory = ThreadFactory { task ->
            Thread(task, "Nocturne-BanTracker").apply { isDaemon = true }
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(factory).also { executor ->
            executor.scheduleAtFixedRate({ pollSafely(currentGeneration) }, 0L, POLL_SECONDS, TimeUnit.SECONDS)
        }
    }

    override fun onDisable() {
        generation++
        stopScheduler()
        synchronized(lock) { hasBaseline = false }
    }

    private fun stopScheduler() {
        scheduler?.shutdownNow()
        scheduler = null
    }

    private fun pollSafely(currentGeneration: Long) {
        try {
            val totals = requestTotals()
            if (currentGeneration != generation || scheduler == null) return

            val increases = synchronized(lock) {
                if (!hasBaseline) {
                    hasBaseline = true
                    previousWatchdog = totals.watchdog
                    previousStaff = totals.staff
                    return
                }
                val watchdog = (totals.watchdog - previousWatchdog).coerceAtLeast(0)
                val staff = (totals.staff - previousStaff).coerceAtLeast(0)
                previousWatchdog = totals.watchdog
                previousStaff = totals.staff
                watchdog to staff
            }
            if (increases.first > 0 || increases.second > 0) {
                queueNotification(currentGeneration, increases.first, increases.second)
            }
        } catch (_: Exception) {
            // Retry on the next scheduled poll without spamming the chat.
        }
    }

    private fun queueNotification(currentGeneration: Long, watchdog: Int, staff: Int) {
        Minecraft.getInstance().execute {
            if (!enabled || currentGeneration != generation) return@execute
            if (watchdog > 0) {
                ChatUtil.send(
                    Component.literal("Watchdog banned $watchdog player(s)")
                        .withStyle(ChatFormatting.GRAY)
                )
            }
            if (staff > 0) {
                ChatUtil.send(
                    Component.literal("Staff banned $staff player(s)")
                        .withStyle(ChatFormatting.GRAY)
                )
            }
        }
    }

    private fun requestTotals(): Totals {
        val connection = URI.create(API_URL).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Nocturne BanTracker")
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Ban tracker returned HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            val lastData = Regex("\\\"lastData\\\"\\s*:\\s*\\{([^}]*)}")
                .find(body)?.groupValues?.get(1)
                ?: throw IllegalStateException("Missing lastData")
            val watchdog = Regex("\\\"watchdog_total\\\"\\s*:\\s*(\\d+)")
                .find(lastData)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IllegalStateException("Missing watchdog_total")
            val staff = Regex("\\\"staff_total\\\"\\s*:\\s*(\\d+)")
                .find(lastData)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IllegalStateException("Missing staff_total")
            return Totals(watchdog, staff)
        } finally {
            connection.disconnect()
        }
    }

    private data class Totals(val watchdog: Int, val staff: Int)

    private const val API_URL = "https://bans.fisproxy.org/stats"
    private const val POLL_SECONDS = 5L
    private const val TIMEOUT_MILLIS = 5_000
}
