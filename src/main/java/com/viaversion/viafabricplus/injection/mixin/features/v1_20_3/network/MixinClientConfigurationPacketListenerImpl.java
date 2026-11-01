package com.viaversion.viafabricplus.injection.mixin.features.v1_20_3.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class MixinClientConfigurationPacketListenerImpl extends ClientCommonPacketListenerImpl {

    protected MixinClientConfigurationPacketListenerImpl(Minecraft client, Connection connection, CommonListenerCookie connectionState) {
        super(client, connection, connectionState);
    }

    @Inject(method = "handleConfigurationFinished", at = @At("HEAD"))
    private void disableAutoRead(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            this.connection.channel.config().setAutoRead(false);
        }
    }

    @Inject(method = "handleConfigurationFinished", at = @At("RETURN"))
    private void enableAutoRead(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
            this.connection.channel.config().setAutoRead(true);
        }
    }

}
