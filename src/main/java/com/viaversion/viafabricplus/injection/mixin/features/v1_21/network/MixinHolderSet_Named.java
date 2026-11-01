package com.viaversion.viafabricplus.injection.mixin.features.v1_21.network;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HolderSet.Named.class)
public abstract class MixinHolderSet_Named {

    @Shadow
    private @Nullable List<Holder> contents;

    @Shadow
    abstract void bind(List<Holder> contents);

    @Inject(method = "contents", at = @At("HEAD"))
    private void preventNullableEntries(CallbackInfoReturnable<List<Holder<?>>> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {

            if (this.contents == null) {
                this.bind(List.of());
            }
        }
    }

    @Inject(method = "isBound", at = @At("HEAD"), cancellable = true)
    private void alwaysBound(CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {

            cir.setReturnValue(true);
        }
    }

}
