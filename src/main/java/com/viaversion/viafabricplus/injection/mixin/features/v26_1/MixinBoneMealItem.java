package com.viaversion.viafabricplus.injection.mixin.features.v26_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BoneMealItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoneMealItem.class)
public abstract class MixinBoneMealItem {

    @Inject(method = "useOn", at = @At(value = "RETURN", ordinal = 1), cancellable = true)
    private void dontSwingHand(CallbackInfoReturnable<InteractionResult> cir) {
        if (ViaFabricPlus.api().targetVersion().equalTo(ProtocolVersion.v26_1)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

}
