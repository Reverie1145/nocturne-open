package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.particle;

import com.viaversion.viafabricplus.features.v1_12_2.FootStepParticle1_12_2;
import com.viaversion.viaversion.api.data.MappingDataBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MappingDataBase.class, remap = false)
public abstract class MixinMappingDataBase {

    @Inject(method = "getNewParticleId", at = @At("HEAD"), cancellable = true)
    private void passthroughFootStepParticle(int id, CallbackInfoReturnable<Integer> cir) {
        if (id == FootStepParticle1_12_2.RAW_ID) {
            cir.setReturnValue(id);
        }
    }

}
