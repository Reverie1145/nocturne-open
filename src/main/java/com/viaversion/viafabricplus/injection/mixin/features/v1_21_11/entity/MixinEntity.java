package com.viaversion.viafabricplus.injection.mixin.features.v1_21_11.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    protected abstract @Nullable AABB modifyPassengerFluidInteractionBox(final AABB passengerBox);

    @Redirect(method = "getFluidInteractionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;modifyPassengerFluidInteractionBox(Lnet/minecraft/world/phys/AABB;)Lnet/minecraft/world/phys/AABB;"))
    private AABB skipPassengerChanges(Entity instance, AABB passengerBox) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return passengerBox;
        } else {
            return modifyPassengerFluidInteractionBox(passengerBox);
        }
    }

}
