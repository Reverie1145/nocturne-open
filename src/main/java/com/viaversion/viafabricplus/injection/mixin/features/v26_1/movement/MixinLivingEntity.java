package com.viaversion.viafabricplus.injection.mixin.features.v26_1.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @ModifyConstant(method = "getFrictionInfluencedSpeed", constant = @Constant(doubleValue = 0.6))
    private double allowFrictionLessThan(double constant) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return Double.MIN_VALUE;
        } else {
            return constant;
        }
    }

    @Redirect(method = "computeModifiedFriction", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private static float dontClampFriction(float value, float min, float max) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return value;
        } else {
            return Mth.clamp(value, min, max);
        }
    }

}
