package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.ConnectException;
import java.net.SocketException;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class MixinConnection extends SimpleChannelInboundHandler<Packet<?>> {

    @Inject(method = "exceptionCaught", at = @At("HEAD"))
    private void printNetworkingErrors(ChannelHandlerContext ctx, Throwable cause, CallbackInfo ci) {
        if (ViaFabricPlusImpl.impl().advanced().printNetworkingErrorsToLogs().isActive()) {
            if (cause instanceof SocketException || cause instanceof ConnectException) {

                return;
            }
            ViaFabricPlusImpl.impl().logger().error("An exception occurred while handling a packet", cause);
        }
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {

        if (evt.getClass().getName().equals("me.steinborn.krypton.mod.shared.misc.KryptonPipelineEvent")) {
            if (evt.toString().equals("COMPRESSION_ENABLED")) {
                ViaChannelInitializer.reorderPipeline(ctx.pipeline(), HandlerNames.COMPRESS, HandlerNames.DECOMPRESS);
                ViaFabricPlusImpl.impl().logger().warn("ViaFabricPlus has detected that the Krypton mod is installed. Please note that Krypton is mostly snake oil on the client side, and it is not recommended to use it.");
                return;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

}
