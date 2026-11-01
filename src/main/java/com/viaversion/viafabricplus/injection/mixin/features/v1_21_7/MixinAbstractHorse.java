package com.viaversion.viafabricplus.injection.mixin.features.v1_21_7;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractHorse.class)
public abstract class MixinAbstractHorse {

    @WrapWithCondition(method = "addPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;absSnapRotationTo(FF)V"))
    private boolean dontSetPassengerRotation(Entity instance, float yRot, float xRot) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_21_7);
    }

}
