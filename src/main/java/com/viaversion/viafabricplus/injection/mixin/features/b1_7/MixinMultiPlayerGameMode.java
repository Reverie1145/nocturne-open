package com.viaversion.viafabricplus.injection.mixin.features.b1_7;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "hasExperience", at = @At("HEAD"), cancellable = true)
    private void removeExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlusImpl.impl().visuals().hideModernHUDElements().isActive()) {
            cir.setReturnValue(false);
        }
    }

}
