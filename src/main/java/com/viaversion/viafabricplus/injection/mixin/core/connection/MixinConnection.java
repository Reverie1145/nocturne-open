package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.injection.access.core.ILocalSampleLogger;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import com.viaversion.viafabricplus.protocoltranslator.util.ProtocolVersionDetector;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.InetSocketAddress;
import javax.crypto.Cipher;
import net.minecraft.network.CipherDecoder;
import net.minecraft.network.CipherEncoder;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.netty.PreNettyLengthPrepender;
import net.raphimc.vialegacy.netty.PreNettyLengthRemover;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Connection.class)
public abstract class MixinConnection extends SimpleChannelInboundHandler<Packet<?>> implements IConnection {

    @Shadow
    public Channel channel;

    @Shadow
    public abstract void channelActive(@NotNull ChannelHandlerContext ctx) throws Exception;

    @Unique
    private UserConnection viaFabricPlus$userConnection;

    @Unique
    private ProtocolVersion viaFabricPlus$serverVersion;

    @Unique
    private Cipher viaFabricPlus$decryptionCipher;

    @Inject(method = "setupCompression", at = @At("RETURN"))
    private void reorderCompression(int threshold, boolean validateDecompressed, CallbackInfo ci) {

        ViaChannelInitializer.reorderPipeline(channel.pipeline(), HandlerNames.COMPRESS, HandlerNames.DECOMPRESS);
    }

    @Inject(method = "setEncryptionKey", at = @At("HEAD"), cancellable = true)
    private void storeDecryptionCipher(Cipher decryptCipher, Cipher encryptCipher, CallbackInfo ci) {
        if (this.viaFabricPlus$serverVersion != null   && this.viaFabricPlus$serverVersion.olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {

            ci.cancel();

            this.viaFabricPlus$decryptionCipher = decryptCipher;

            if (encryptCipher == null) {
                throw new IllegalStateException("Encryption cipher is null");
            }

            this.channel.pipeline().addBefore(PreNettyLengthRemover.NAME, HandlerNames.ENCRYPT, new CipherEncoder(encryptCipher));
        }
    }

    @Inject(method = "connectToServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connect(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;"))
    private static void setTargetVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, LocalSampleLogger bandwidthLogger, CallbackInfoReturnable<Connection> cir, @Local(name = "connection") Connection connection) {

        if (bandwidthLogger instanceof ILocalSampleLogger mixinMultiValueDebugSampleLogImpl && mixinMultiValueDebugSampleLogImpl.viaFabricPlus$getForcedVersion() != null) {
            ((IConnection) connection).viaFabricPlus$setTargetVersion(mixinMultiValueDebugSampleLogImpl.viaFabricPlus$getForcedVersion());
        }
    }

    @WrapWithCondition(method = "connectToServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setBandwidthLogger(Lnet/minecraft/util/debugchart/LocalSampleLogger;)V"))
    private static boolean dontSetPerformanceLog(Connection instance, LocalSampleLogger bandwidthLogger) {

        return !(bandwidthLogger instanceof ILocalSampleLogger mixinMultiValueDebugSampleLogImpl) || mixinMultiValueDebugSampleLogImpl.viaFabricPlus$getForcedVersion() == null;
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private static void setTargetVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, Connection connection, CallbackInfoReturnable<ChannelFuture> cir) {
        ProtocolVersion targetVersion = ((IConnection) connection).viaFabricPlus$getTargetVersion();
        if (targetVersion == null) {
            targetVersion = ViaFabricPlus.api().targetVersion();
        }
        if (targetVersion == ProtocolVersionDetector.AUTO_DETECT_VERSION) {
            targetVersion = ProtocolTranslationImpl.NATIVE_VERSION;
        }
        ((IConnection) connection).viaFabricPlus$setTargetVersion(targetVersion);
    }

    @Override
    public void viaFabricPlus$setupPreNettyDecryption() {
        if (this.viaFabricPlus$decryptionCipher == null) {
            throw new IllegalStateException("Decryption cipher is null");
        }

        this.channel.pipeline().addBefore(PreNettyLengthPrepender.NAME, HandlerNames.DECRYPT, new CipherDecoder(this.viaFabricPlus$decryptionCipher));
    }

    @Override
    public UserConnection viaFabricPlus$getUserConnection() {
        return this.viaFabricPlus$userConnection;
    }

    @Override
    public void viaFabricPlus$setUserConnection(UserConnection connection) {
        this.viaFabricPlus$userConnection = connection;
    }

    @Override
    public ProtocolVersion viaFabricPlus$getTargetVersion() {
        return this.viaFabricPlus$serverVersion;
    }

    @Override
    public void viaFabricPlus$setTargetVersion(final ProtocolVersion serverVersion) {
        this.viaFabricPlus$serverVersion = serverVersion;
    }

}
