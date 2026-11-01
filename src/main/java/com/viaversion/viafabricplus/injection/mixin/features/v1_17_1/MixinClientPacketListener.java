package com.viaversion.viafabricplus.injection.mixin.features.v1_17_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    public abstract void handleSetSimulationDistance(final ClientboundSetSimulationDistancePacket packet);

    @Inject(method = "handleSetChunkCacheRadius", at = @At("RETURN"))
    private void emulateSimulationDistance(ClientboundSetChunkCacheRadiusPacket packet, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_17_1)) {
            this.handleSetSimulationDistance(new ClientboundSetSimulationDistancePacket(packet.getRadius()));
        }
    }

}
