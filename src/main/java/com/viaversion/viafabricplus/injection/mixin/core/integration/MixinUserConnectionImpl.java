package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.viaversion.viafabricplus.protocoltranslator.util.NoPacketSendChannel;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = UserConnectionImpl.class, remap = false)
public abstract class MixinUserConnectionImpl {

    @Shadow
    @Final
    private Channel channel;

    @Inject(method = "sendRawPacket(Lio/netty/buffer/ByteBuf;Z)V", at = @At("HEAD"), cancellable = true)
    private void handleNoPacketSendChannel(ByteBuf packet, boolean currentThread, CallbackInfo ci) {
        if (this.channel instanceof NoPacketSendChannel) {
            ci.cancel();
        }
    }

}
