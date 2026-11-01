package com.viaversion.viafabricplus.injection.mixin.features.v1_17;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class MixinClientHandshakePacketListenerImpl {

    @Redirect(method = "handleCompression", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupCompression(IZ)V"))
    private void pre1_17_1CompressionBehaviour(Connection instance, int threshold, boolean validateDecompressed) {
        instance.setupCompression(threshold, ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_17));
    }

}
