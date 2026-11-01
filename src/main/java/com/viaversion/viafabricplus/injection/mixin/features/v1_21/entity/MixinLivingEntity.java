package com.viaversion.viafabricplus.injection.mixin.features.v1_21.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double dontLimitJumpVelocity(double a, double b) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return a;
        } else {
            return Math.max(a, b);
        }
    }

}
