package com.viaversion.viafabricplus.injection.mixin.features.v1_20_5.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_20_5.EnchantmentAttributesEmulation1_20_6;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 999  )
public abstract class MixinLivingEntity {

    @ModifyExpressionValue(method = "tickEffects", at = @At(value = "CONSTANT", args = "intValue=4"))
    private int changeParticleDensity(int original) {
        if (ViaFabricPlus.api().targetVersion().olderThan(ProtocolVersion.v1_20_5)) {
            return 2;
        } else {
            return original;
        }
    }

    @Inject(method = "getBlockSpeedFactor", at = @At("HEAD"))
    private void setGenericMovementEfficiencyAttribute(CallbackInfoReturnable<Float> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            EnchantmentAttributesEmulation1_20_6.setGenericMovementEfficiencyAttribute((LivingEntity) (Object) this);
        }
    }

}
