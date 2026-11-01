package cn.reverie.module.player

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.module.NumberSetting
import net.minecraft.client.Minecraft

object AutoRespawn : Module(
    name = "AutoRespawn",
    description = "Respawn automatically after death",
    category = Category.PLAYER
) {
    val delay = NumberSetting("Delay", "Delay before respawning in ticks", 20.0, 5.0, 100.0, 5.0)
    private var deadTicks = 0
    private var requested = false

    override fun onEnable() = onWorldChange()
    override fun onDisable() = onWorldChange()

    fun onWorldChange() {
        deadTicks = 0
        requested = false
    }

    fun onTick() {
        if (!enabled) return
        val player = Minecraft.getInstance().player ?: run { onWorldChange(); return }
        if (player.isAlive) { onWorldChange(); return }
        if (requested) return
        if (++deadTicks >= delay.getInt()) {
            requested = true
            player.respawn()
        }
    }
}
