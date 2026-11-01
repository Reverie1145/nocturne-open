package cn.reverie.module.player

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import cn.reverie.render.ui.notification.NotificationManager

object LowHealthAlert : Module(
    name = "LowHealthAlert",
    description = "Show a warning when health falls below a chosen level",
    category = Category.PLAYER
) {
    val hearts = NumberSetting("Hearts", "Alert at this many hearts or below", 4.0, 0.5, 10.0, 0.5)
    private var alerted = false

    override fun onEnable() = onWorldChange()
    override fun onDisable() = onWorldChange()
    fun onWorldChange() { alerted = false }

    fun onTick() {
        if (!enabled) return
        val player = Minecraft.getInstance().player ?: run { onWorldChange(); return }
        if (!player.isAlive) return
        if (player.health > hearts.value * 2.0) {
            alerted = false
        } else if (!alerted) {
            alerted = true
            NotificationManager.warning("Low Health", "Only ${"%.1f".format(player.health / 2.0f)} hearts remaining")
        }
    }
}
