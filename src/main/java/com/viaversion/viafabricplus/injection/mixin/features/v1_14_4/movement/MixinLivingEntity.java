package com.viaversion.viafabricplus.injection.mixin.features.v1_14_4.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Redirect(method = "canGlide", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
    private boolean allowElytraInVehicle(LivingEntity instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_14_4) && instance.isPassenger();
    }

}
