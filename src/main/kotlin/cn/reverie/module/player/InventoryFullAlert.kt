package cn.reverie.module.player

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft
import cn.reverie.render.ui.notification.NotificationManager

object InventoryFullAlert : Module(
    name = "InventoryFullAlert",
    description = "Warn before inventory and hotbar run out of space",
    category = Category.PLAYER
) {
    val freeSlots = NumberSetting("Free Slots", "Warn when this many or fewer slots remain", 2.0, 0.0, 10.0, 1.0)
    private var alerted = false
    private var ticks = 0

    override fun onEnable() = onWorldChange()
    override fun onDisable() = onWorldChange()
    fun onWorldChange() { alerted = false; ticks = 0 }

    fun onTick() {
        if (!enabled) return
        val player = Minecraft.getInstance().player ?: run { onWorldChange(); return }
        if (++ticks < 20) return
        ticks = 0
        val free = (0..35).count { player.inventory.getItem(it).isEmpty }
        if (free > freeSlots.getInt()) {
            alerted = false
        } else if (!alerted) {
            alerted = true
            NotificationManager.warning("Inventory Space", "$free free slot(s) remaining")
        }
    }
}
