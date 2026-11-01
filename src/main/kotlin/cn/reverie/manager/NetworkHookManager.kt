package cn.reverie.manager

import cn.reverie.app.event.EventBus
import cn.reverie.app.event.packet.PacketEvent
import cn.reverie.app.event.packet.PacketSide
import cn.reverie.module.misc.FlagDeleter
import cn.reverie.module.combat.Criticals
import cn.reverie.module.combat.killaura.KillAuraWatchdogBlock
import com.viaversion.viaversion.api.protocol.Protocol
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import cn.reverie.util.network.PacketUtil
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket

object NetworkHookManager {
    @JvmStatic fun publishAgentChat(message: String, direction: String) =
        cn.reverie.app.agent.AgentManager.onChat(message, direction)
    @JvmStatic fun onPositionCorrection(packet: ClientboundPlayerPositionPacket) = FlagDeleter.onPositionCorrection(packet)

    /** Via-generated input/action packets must share Aura's native packet FIFO. */
    @JvmStatic
    fun captureAuraProtocolPacket(packet: PacketWrapper, protocol: Class<out Protocol<*, *, *, *>>): Boolean =
        KillAuraWatchdogBlock.captureProtocolPacket(packet, protocol)

    @JvmStatic
    fun publishPacket(packet: Packet<*>, side: PacketSide): Boolean {
        val noEvent = PacketUtil.isNoEvent(packet)
        if (side == PacketSide.CLIENT && Criticals.suppressesPacket(packet)) return true
        if (side == PacketSide.SERVER) Criticals.onPacketReceive(packet)
        if (noEvent) {
            if (side == PacketSide.CLIENT) Criticals.onPacketSent(packet)
            return false
        }
        val event = PacketEvent(packet, side)
        EventBus.post(event)
        if (!event.isCancelled && side == PacketSide.CLIENT) Criticals.onPacketSent(event.packet)
        return event.isCancelled
    }
}
