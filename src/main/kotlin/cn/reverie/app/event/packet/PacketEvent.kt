package cn.reverie.app.event.packet

import cn.reverie.app.event.Cancellable
import net.minecraft.network.protocol.Packet

class PacketEvent(
    var packet: Packet<*>,
    val side: PacketSide
) : Cancellable {
    override var isCancelled: Boolean = false

    val isSend: Boolean
        get() = side == PacketSide.CLIENT

    val isReceive: Boolean
        get() = side == PacketSide.SERVER
}
