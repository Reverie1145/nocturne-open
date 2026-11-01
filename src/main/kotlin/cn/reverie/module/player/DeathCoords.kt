package cn.reverie.module.player

import cn.reverie.module.Category
import cn.reverie.module.Module
import cn.reverie.util.misc.ChatUtil
import net.minecraft.client.Minecraft

object DeathCoords : Module(
    name = "DeathCoords",
    description = "Record your death location in client chat",
    category = Category.PLAYER
) {
    private var reported = false

    override fun onEnable() = onWorldChange()
    override fun onDisable() = onWorldChange()

    fun onWorldChange() {
        reported = false
    }

    fun onTick() {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: run { onWorldChange(); return }
        if (player.isAlive) { reported = false; return }
        if (reported) return
        reported = true
        val pos = player.blockPosition()
        val dimension = mc.level?.dimension()?.toString() ?: "unknown"
        ChatUtil.sendInfo("Death at ${pos.x}, ${pos.y}, ${pos.z} ($dimension)")
    }
}
