package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.net.URL;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl {

    @Shadow
    @Final
    protected Connection connection;

    @Shadow
    public abstract void send(Packet<?> packet);

    @Shadow
    @Nullable
    private static URL parseResourcePackUrl(String urlString) {
        return null;
    }

    @Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
    private void validateUrlInNetworkThread(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            if (parseResourcePackUrl(packet.url()) == null) {
                this.connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.INVALID_URL));
                ci.cancel();
            }
        }
    }

}
