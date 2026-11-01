package com.viaversion.viafabricplus.injection.mixin.features.c0_30cpe;

import com.viaversion.viafabricplus.features.c0_30cpe.CPEAdditions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Inject(method = "getPrecipitationAt", at = @At(value = "HEAD"), cancellable = true)
    private void forceSnow(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (CPEAdditions.isSnowing()) {
            cir.setReturnValue(Biome.Precipitation.SNOW);
        }
    }

}
