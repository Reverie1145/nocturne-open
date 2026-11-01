package cn.reverie.mixin.network;

import cn.reverie.manager.NetworkHookManager;
import cn.reverie.app.event.packet.PacketSide;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinConnection {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"), cancellable = true)
    private void nocturne$onSendPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (NetworkHookManager.publishPacket(packet, PacketSide.CLIENT)) {
            ci.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void nocturne$onChannelRead(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (NetworkHookManager.publishPacket(packet, PacketSide.SERVER)) {
            ci.cancel();
        }
    }
}
