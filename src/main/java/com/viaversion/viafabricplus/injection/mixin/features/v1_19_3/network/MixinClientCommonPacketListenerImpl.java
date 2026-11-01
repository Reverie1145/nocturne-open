package com.viaversion.viafabricplus.injection.mixin.features.v1_19_3.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl {

    @Shadow
    protected abstract void sendWhen(Packet<? extends ServerboundPacketListener> packet, BooleanSupplier sendCondition, Duration expiry);

    @Shadow
    public abstract void send(Packet<?> packet);

    @Redirect(method = "handleKeepAlive", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientCommonPacketListenerImpl;sendWhen(Lnet/minecraft/network/protocol/Packet;Ljava/util/function/BooleanSupplier;Ljava/time/Duration;)V"))
    private void forceSendKeepAlive(ClientCommonPacketListenerImpl instance, Packet<? extends ServerboundPacketListener> packet, BooleanSupplier condition, Duration expireAfterDuration) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            send(packet);
        } else {
            sendWhen(packet, condition, expireAfterDuration);
        }
    }

}
