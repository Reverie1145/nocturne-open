package com.viaversion.viafabricplus.injection.mixin.compat.fabricapi;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import net.fabricmc.fabric.impl.client.registry.sync.ClientRegistrySyncHandler;
import net.fabricmc.fabric.impl.registry.sync.packet.RegistrySyncPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientRegistrySyncHandler.class)
public abstract class MixinClientRegistrySyncHandler {

    @Inject(method = "checkRemoteRemap", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;)V", ordinal = 0), cancellable = true, remap = false)
    private static void ignoreFabricSyncErrors(RegistrySyncPayload data, CallbackInfo ci) {
        if (ViaFabricPlusImpl.impl().advanced().ignoreFabricSyncErrors().isActive()) {
            ViaFabricPlusImpl.impl().logger().warn("Ignoring missing registries from Fabric API");
            ci.cancel();
        }
    }

}
