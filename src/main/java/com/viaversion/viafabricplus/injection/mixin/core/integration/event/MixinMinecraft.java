package com.viaversion.viafabricplus.injection.mixin.core.integration.event;

import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void callPostGameLoadEvent(GameConfig gameConfig, CallbackInfo ci) {
        FabricLoader.getInstance().invokeEntrypoints("viafabricplus", ViaFabricPlusEntrypoint.class, ViaFabricPlusEntrypoint::onPostGameLoading);
    }

}
