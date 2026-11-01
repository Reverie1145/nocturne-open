package cn.reverie.module.combat.killaura

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket
import net.minecraft.network.protocol.common.ServerboundPongPacket
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KillAuraBlinkPacketChecks {
    @Test
    fun transactionConfirmationsCannotOvertakeTheBufferedFlyingPacket() {
        val sent = mutableListOf<Packet<*>>()
        val buffer = KillAuraBlinkBuffer<Packet<*>> { sent += it }
        val flying = ServerboundMovePlayerPacket.StatusOnly(true, false)
        val transaction = ServerboundPongPacket(17)
        val keepAlive = ServerboundKeepAlivePacket(42L)
        buffer.blink()
        for (packet in listOf(flying, transaction, keepAlive)) {
            if (!KillAuraBlinkPackets.includes(packet) || !buffer.capture(packet)) sent += packet
        }
        assertTrue(sent.isEmpty(), "A transaction must not be sent while its preceding flying packet is held")
        buffer.flush()
        assertEquals(listOf<Packet<*>>(flying, transaction, keepAlive), sent)
        assertTrue(buffer.active)
    }
}
