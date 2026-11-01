package com.viaversion.viafabricplus.injection.mixin.features.v1_21.network;

import com.mojang.serialization.DataResult;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HolderSetCodec.class)
public abstract class MixinHolderSetCodec {

    @Inject(method = "lookupTag", at = @At("RETURN"), cancellable = true)
    private static void workaroundValidation(HolderGetter<?> registry, TagKey<?> key, CallbackInfoReturnable<DataResult<HolderSet<?>>> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {

            if (cir.getReturnValue().isError()) {
                cir.setReturnValue(DataResult.success(HolderSet.empty()));
            }
        }
    }

}
