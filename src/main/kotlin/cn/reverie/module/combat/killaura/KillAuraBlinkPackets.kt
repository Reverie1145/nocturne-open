package cn.reverie.module.combat.killaura

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

internal object KillAuraBlinkPackets {
    fun includes(packet: Packet<*>): Boolean {
        val packetPackage = packet.javaClass.packageName
        // Via translates common PONG into legacy transaction confirmations.
        // It must follow the queued gameplay packets, including the flying packet.
        return packetPackage == ServerboundMovePlayerPacket::class.java.packageName ||
            packetPackage == ServerboundPongPacket::class.java.packageName
    }
}
