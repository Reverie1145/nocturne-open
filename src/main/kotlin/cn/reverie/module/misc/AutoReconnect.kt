package cn.reverie.module.misc

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import cn.reverie.render.ui.notification.NotificationManager

/** Remembers the last active multiplayer server; never reconnects from normal menus. */
object AutoReconnect : Module(
    name = "AutoReconnect",
    description = "Retry a multiplayer server after an unexpected disconnect",
    category = Category.MISC
) {
    val delay = NumberSetting("Delay", "Seconds between attempts", 10.0, 3.0, 60.0, 1.0)
    val maxAttempts = NumberSetting("Max Attempts", "0 means unlimited retries", 5.0, 0.0, 20.0, 1.0)

    private var lastServer: ServerData? = null
    private var attempts = 0
    private var ticksOnDisconnect = 0
    private var limitNotified = false

    override fun onEnable() = reset()
    override fun onDisable() = reset()

    fun onTick() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        if (mc.level != null) {
            mc.currentServer?.let { lastServer = it }
            attempts = 0
            ticksOnDisconnect = 0
            limitNotified = false
            return
        }
        val screen = mc.gui.screen()
        if (screen !is DisconnectedScreen) {
            ticksOnDisconnect = 0
            return
        }
        val server = lastServer ?: return
        val limit = maxAttempts.getInt()
        if (limit > 0 && attempts >= limit) {
            if (!limitNotified) {
                NotificationManager.warning("AutoReconnect", "Stopped after $attempts attempts")
                limitNotified = true
            }
            return
        }
        if (++ticksOnDisconnect < delay.getInt() * 20) return
        ticksOnDisconnect = 0
        attempts++
        ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(server.ip), server, false, null)
    }

    private fun reset() {
        lastServer = null
        attempts = 0
        ticksOnDisconnect = 0
        limitNotified = false
    }
}
