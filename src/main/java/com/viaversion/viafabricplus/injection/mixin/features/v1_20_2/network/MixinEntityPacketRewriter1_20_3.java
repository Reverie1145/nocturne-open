package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2.network;

import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.rewriter.EntityPacketRewriter1_20_3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(value = EntityPacketRewriter1_20_3.class, remap = false)
public abstract class MixinEntityPacketRewriter1_20_3 {

    @Overwrite
    private void sendChunksSentGameEvent(final PacketWrapper wrapper) {
    }

}
