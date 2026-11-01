package com.viaversion.viafabricplus.injection.mixin.features.v1_15;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Animal.class)
public abstract class MixinAnimal {

    @Redirect(method = "mobInteract", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isClientSide()Z"))
    private boolean changeIsClientCondition(Level instance) {
        return instance.isClientSide() && ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_15);
    }

}
