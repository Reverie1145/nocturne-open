package cn.reverie.util.network

import cn.reverie.mixin.network.ServerboundMovePlayerPacketAccessor
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.protocols.v26_1to26_2.Protocol26_1To26_2
import com.viaversion.viaversion.protocols.v26_1to26_2.storage.ProtocolStorables26_2
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

object PacketUtil {
    private val noEventPackets = Collections.newSetFromMap(ConcurrentHashMap<Packet<*>, Boolean>())

    @JvmStatic
    fun legacyEntityId(connection: UserConnection, entityId: Int): Int {
        if (entityId >= -1) return entityId
        val storage = connection.storables<ProtocolStorables26_2>(Protocol26_1To26_2::class.java)
            ?: return entityId
        return if (entityId == storage.fakeEntityId()) 0 else entityId
    }

    fun sendPacket(packet: Packet<*>) {
        val mc = Minecraft.getInstance()
        mc.connection?.send(packet)
    }

    fun sendPacketNoEvent(packet: Packet<*>) {
        noEventPackets.add(packet)
        sendPacket(packet)
    }

    fun isNoEvent(packet: Packet<*>): Boolean {
        return noEventPackets.remove(packet)
    }

    fun sendSilent(packet: Packet<*>) {
        sendPacketNoEvent(packet)
    }

    fun receiveSilent(packet: Packet<*>) {
        val listener = Minecraft.getInstance().connection ?: return
        if (!listener.shouldHandleMessage(packet)) return
        @Suppress("UNCHECKED_CAST")
        (packet as Packet<ClientGamePacketListener>).handle(listener)
    }

    fun setPlayerMovePacketYaw(packet: ServerboundMovePlayerPacket, yaw: Float) {
        (packet as ServerboundMovePlayerPacketAccessor).nocturneSetYRot(yaw)
    }

    fun setPlayerMovePacketPitch(packet: ServerboundMovePlayerPacket, pitch: Float) {
        (packet as ServerboundMovePlayerPacketAccessor).nocturneSetXRot(pitch)
    }

    fun setPlayerMovePacketY(packet: ServerboundMovePlayerPacket, y: Double) {
        (packet as ServerboundMovePlayerPacketAccessor).nocturneSetY(y)
    }

    fun setPlayerMovePacketOnGround(packet: ServerboundMovePlayerPacket, onGround: Boolean) {
        (packet as ServerboundMovePlayerPacketAccessor).nocturneSetOnGround(onGround)
    }
}
