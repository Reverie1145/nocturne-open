package cn.reverie.module.combat.killaura

import com.viaversion.viaversion.api.minecraft.BlockPosition
import com.viaversion.viaversion.api.minecraft.item.Item
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.type.Types

/** Payloads of the original 1.8 C02 and C08 constructors. */
internal object KillAuraLegacyPackets {
    fun writeInteractAt(packet: PacketWrapper, entityId: Int, x: Double, y: Double, z: Double) {
        packet.write(Types.VAR_INT, entityId)
        packet.write(Types.VAR_INT, 2)
        packet.write(Types.FLOAT, x.toFloat())
        packet.write(Types.FLOAT, y.toFloat())
        packet.write(Types.FLOAT, z.toFloat())
    }

    fun writeInteract(packet: PacketWrapper, entityId: Int) {
        packet.write(Types.VAR_INT, entityId)
        packet.write(Types.VAR_INT, 0)
    }

    fun writeSwordBlock(packet: PacketWrapper, item: Item) {
        packet.write(Types.BLOCK_POSITION1_8, BlockPosition(-1, -1, -1))
        packet.write(Types.UNSIGNED_BYTE, 255.toShort())
        packet.write(Types.ITEM1_8, item.copy())
        repeat(3) { packet.write(Types.UNSIGNED_BYTE, 0.toShort()) }
    }
}
