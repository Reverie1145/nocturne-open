package com.viaversion.viafabricplus.injection.mixin.features.v1_18;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Redirect(method = "updateFallFlyingMovement", at = @At(value = "INVOKE", target = "Ljava/lang/Math;cos(D)D", remap = false))
    private double fixCosTable(double a) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18)) {
            return Mth.cos((float) a);
        } else {
            return Math.cos(a);
        }
    }

}
