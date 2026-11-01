package com.viaversion.viafabricplus.injection.mixin.features.v1_21_11.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityFluidInteraction.Tracker.class)
public abstract class MixinEntityFluidInteraction_Tracker {

    @Redirect(method = "applyCurrentTo", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D"))
    private double useLengthInstead(Vec3 instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return instance.length();
        } else {
            return instance.lengthSqr();
        }
    }

    @ModifyConstant(method = "applyCurrentTo", constant = @Constant(doubleValue = (double) 1.0E-5F))
    private double changeThreshold(double constant) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return 0;
        } else {
            return constant;
        }
    }

}
