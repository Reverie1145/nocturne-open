package com.viaversion.viafabricplus.injection.mixin.features.v1_5_2;

import com.mojang.authlib.GameProfile;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.Protocol1_21To1_21_2;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.storage.ClientVehicleStorage;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.storage.ProtocolStorables1_21_2;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.release.r1_5_2tor1_6_1.Protocolr1_5_2Tor1_6_1;
import net.raphimc.vialegacy.protocol.release.r1_5_2tor1_6_1.packet.ServerboundPackets1_5_2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer {

    @Shadow
    @Final
    public ClientPacketListener connection;

    public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Redirect(method = "tick", slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z")), at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0))
    private void modifyPositionPacket(ClientPacketListener instance, Packet<?> packet) {
        if (ViaFabricPlus.api().targetVersion().newerThan(LegacyProtocolVersion.r1_5_2)) {
            instance.send(packet);
            return;
        }

        final UserConnection connection = ((IConnection) this.connection.getConnection()).viaFabricPlus$getUserConnection();
        connection.getChannel().eventLoop().execute(() -> {
            final PacketWrapper movePlayerPosRot = PacketWrapper.create(ServerboundPackets1_5_2.MOVE_PLAYER_POS_ROT, connection);
            movePlayerPosRot.write(Types.DOUBLE, this.getDeltaMovement().x);
            movePlayerPosRot.write(Types.DOUBLE, -999.0D);
            movePlayerPosRot.write(Types.DOUBLE, -999.0D);
            movePlayerPosRot.write(Types.DOUBLE, this.getDeltaMovement().z);
            movePlayerPosRot.write(Types.FLOAT, this.getYRot());
            movePlayerPosRot.write(Types.FLOAT, this.getXRot());
            movePlayerPosRot.write(Types.BOOLEAN, this.onGround());
            movePlayerPosRot.sendToServer(Protocolr1_5_2Tor1_6_1.class);

            final ClientVehicleStorage storage = connection.<ProtocolStorables1_21_2>storables(Protocol1_21To1_21_2.class).clientVehicleStorage();
            if (storage != null) {
                final PacketWrapper playerInput = PacketWrapper.create(ServerboundPackets1_20_5.PLAYER_INPUT, connection);
                playerInput.write(Types.FLOAT, storage.sidewaysMovement());
                playerInput.write(Types.FLOAT, storage.forwardMovement());
                playerInput.write(Types.BYTE, storage.flags());
                playerInput.sendToServer(Protocol1_21To1_21_2.class);
            }
        });
    }

}
