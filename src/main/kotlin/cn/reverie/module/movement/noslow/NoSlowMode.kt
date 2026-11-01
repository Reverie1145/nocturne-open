package cn.reverie.module.movement.noslow

import cn.reverie.app.event.packet.PacketEvent
import net.minecraft.client.player.LocalPlayer

interface NoSlowMode {
    val modeName: String

    fun onEnable() {}

    fun onDisable() {}

    fun onTick() {}

    fun onPreUpdate() {}

    fun onPreMotion(player: LocalPlayer) {}

    fun onSendPosition(yaw: Float, pitch: Float) {}

    fun onPacket(event: PacketEvent) {}

    fun onClick() {}

    fun onItemUseSlowdown(original: Float): Float = original

    fun onWorldChange() {}
}
