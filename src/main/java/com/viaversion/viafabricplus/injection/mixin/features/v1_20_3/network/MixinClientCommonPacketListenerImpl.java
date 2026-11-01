package com.viaversion.viafabricplus.injection.mixin.features.v1_20_3.network;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class MixinClientCommonPacketListenerImpl {

    @WrapWithCondition(method = "onPacketError", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;disconnect(Lnet/minecraft/network/DisconnectionDetails;)V"))
    private boolean dontDisconnectOnPacketException(Connection instance, DisconnectionDetails details) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_20_3);
    }

    @Inject(method = "storeDisconnectionReport", at = @At("HEAD"), cancellable = true)
    private void dontCreatePacketErrorCrashReports(CallbackInfoReturnable<Optional<Path>> cir) {
        if (ViaFabricPlusImpl.impl().advanced().dontCreatePacketErrorCrashReports().isActive()) {
            cir.setReturnValue(Optional.empty());
        }
    }

}
