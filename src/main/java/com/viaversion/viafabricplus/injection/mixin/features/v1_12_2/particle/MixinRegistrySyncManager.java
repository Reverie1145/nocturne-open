package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.features.v1_12_2.FootStepParticle1_12_2;
import net.fabricmc.fabric.impl.registry.sync.RegistrySyncManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RegistrySyncManager.class)
public abstract class MixinRegistrySyncManager {

    @WrapOperation(method = "createAndPopulateRegistryMap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Registry;getKey(Ljava/lang/Object;)Lnet/minecraft/resources/Identifier;"), require = 0)
    private static Identifier skipFootStepParticle(Registry<?> instance, Object t, Operation<Identifier> original) {
        final Identifier id = original.call(instance, t);
        if (id == FootStepParticle1_12_2.ID) {
            return null;
        } else {
            return id;
        }
    }

}
