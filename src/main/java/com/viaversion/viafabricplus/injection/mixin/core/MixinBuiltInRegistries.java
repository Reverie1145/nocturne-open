package com.viaversion.viafabricplus.injection.mixin.core;

import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltInRegistries.class)
public abstract class MixinBuiltInRegistries {

    @Inject(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;createContents()V"))
    private static void callPostRegistryLoad(CallbackInfo ci) {
        FabricLoader.getInstance().invokeEntrypoints("viafabricplus", ViaFabricPlusEntrypoint.class, ViaFabricPlusEntrypoint::onPostRegistryLoading);
    }

}
