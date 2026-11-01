package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.netty.NoReadFlowControlHandler;
import com.viaversion.viafabricplus.protocoltranslator.netty.ViaFabricPlusDecoder;
import com.viaversion.viafabricplus.protocoltranslator.protocol.ViaFabricPlusProtocol;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.netty.PreNettyLengthPrepender;
import net.raphimc.vialegacy.netty.PreNettyLengthRemover;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY;
import static com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl.TARGET_VERSION_ATTRIBUTE_KEY;

@Mixin(targets = "net.minecraft.network.Connection$1")
public abstract class MixinConnection_1 {

    @Final
    @Shadow
    Connection val$connection;

    @Inject(method = "initChannel", at = @At("RETURN"))
    private void injectViaIntoPipeline(Channel channel, CallbackInfo ci) {
        final IConnection mixinClientConnection = (IConnection) this.val$connection;
        final ProtocolVersion serverVersion = mixinClientConnection.viaFabricPlus$getTargetVersion();

        channel.attr(MINECRAFT_CONNECTION_ATTRIBUTE_KEY).set(this.val$connection);
        channel.attr(TARGET_VERSION_ATTRIBUTE_KEY).set(serverVersion);

        final UserConnection user = ViaChannelInitializer.createUserConnection(channel, true);
        mixinClientConnection.viaFabricPlus$setUserConnection(user);

        final ChannelPipeline pipeline = channel.pipeline();

        pipeline.addBefore(HandlerNames.INBOUND_CONFIG, ViaDecodeHandler.NAME, new ViaFabricPlusDecoder(user));
        pipeline.addBefore(HandlerNames.ENCODER, ViaEncodeHandler.NAME, new ViaEncodeHandler(user));

        if (serverVersion.olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {

            pipeline.addBefore(HandlerNames.SPLITTER, PreNettyLengthPrepender.NAME, new PreNettyLengthPrepender(user));
            pipeline.addBefore(HandlerNames.PREPENDER, PreNettyLengthRemover.NAME, new PreNettyLengthRemover(user));
        }

        pipeline.addAfter(ViaDecodeHandler.NAME, NoReadFlowControlHandler.NAME, new NoReadFlowControlHandler());
        user.getProtocolInfo().getPipeline().add(ViaFabricPlusProtocol.INSTANCE);
    }

}
