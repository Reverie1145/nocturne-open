package cn.reverie.module.combat.killaura

import com.viaversion.nbt.tag.CompoundTag
import com.viaversion.viaversion.api.minecraft.item.DataItem
import com.viaversion.viaversion.api.type.Types
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_8
import io.netty.buffer.Unpooled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KillAuraLegacyPacketChecks {
    @Test
    fun legacyEntityInteractionEncodesAtThenFallbackWithoutModernFields() {
        val interactAt = PacketWrapperImpl(ServerboundPackets1_8.INTERACT, null, null)
        val interact = PacketWrapperImpl(ServerboundPackets1_8.INTERACT, null, null)
        KillAuraLegacyPackets.writeInteractAt(interactAt, 300, 0.125, 1.75, -0.375)
        KillAuraLegacyPackets.writeInteract(interact, 300)
        val payload = Unpooled.buffer()
        try {
            interactAt.writeToBuffer(payload)
            assertEquals(0x02, Types.VAR_INT.readPrimitive(payload))
            assertEquals(300, Types.VAR_INT.readPrimitive(payload))
            assertEquals(2, Types.VAR_INT.readPrimitive(payload))
            assertEquals(0.125f, payload.readFloat())
            assertEquals(1.75f, payload.readFloat())
            assertEquals(-0.375f, payload.readFloat())
            assertFalse(payload.isReadable, "Legacy INTERACT_AT has no hand or sneaking fields")
            payload.clear()
            interact.writeToBuffer(payload)
            assertEquals(0x02, Types.VAR_INT.readPrimitive(payload))
            assertEquals(300, Types.VAR_INT.readPrimitive(payload))
            assertEquals(0, Types.VAR_INT.readPrimitive(payload))
            assertFalse(payload.isReadable, "Legacy INTERACT has no hit vector or hand fields")
        } finally {
            payload.release()
        }
    }

    @Test
    fun swordBlockEncodesTheOriginalC08ItemOnlyConstructor() {
        val packet = PacketWrapperImpl(ServerboundPackets1_8.USE_ITEM_ON, null, null)
        KillAuraLegacyPackets.writeSwordBlock(packet, DataItem(276, 1, 0, null))
        val payload = Unpooled.buffer()
        try {
            packet.writeToBuffer(payload)
            assertEquals(0x08, Types.VAR_INT.readPrimitive(payload))
            assertEquals(-1L, payload.readLong(), "The original C08 position is (-1, -1, -1)")
            assertEquals(255, payload.readUnsignedByte().toInt())
            val item = Types.ITEM1_8.read(payload)
            assertEquals(276, item.identifier())
            assertEquals(1, item.amount())
            assertEquals(0, item.data().toInt())
            repeat(3) { assertEquals(0, payload.readUnsignedByte().toInt()) }
            assertFalse(payload.isReadable, "C08(stack) has no hand, sequence or yaw/pitch fields")
        } finally {
            payload.release()
        }
    }

    @Test
    fun bufferedC08KeepsTheItemAndNbtFromTheBlockTick() {
        val tag = CompoundTag().also { it.putInt("RepairCost", 7) }
        val item = DataItem(276, 1, 0, tag)
        val packet = PacketWrapperImpl(ServerboundPackets1_8.USE_ITEM_ON, null, null)
        KillAuraLegacyPackets.writeSwordBlock(packet, item)
        item.setIdentifier(1)
        item.setAmount(64)
        tag.putInt("RepairCost", 42)
        val payload = Unpooled.buffer()
        try {
            packet.writeToBuffer(payload)
            Types.VAR_INT.readPrimitive(payload)
            payload.skipBytes(9)
            val buffered = Types.ITEM1_8.read(payload)
            assertEquals(276, buffered.identifier())
            assertEquals(1, buffered.amount())
            assertEquals(7, checkNotNull(buffered.tag()).getInt("RepairCost"))
        } finally {
            payload.release()
        }
    }
}
