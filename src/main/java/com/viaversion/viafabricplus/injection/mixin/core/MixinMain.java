package com.viaversion.viafabricplus.injection.mixin.core;

import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class MixinMain {

    @Inject(method = "main", at = @At("HEAD"))
    private static void bootstrap(CallbackInfo ci) {
        FabricLoader.getInstance().invokeEntrypoints("viafabricplus", ViaFabricPlusEntrypoint.class, ViaFabricPlusEntrypoint::onPreLoading);
    }

}
