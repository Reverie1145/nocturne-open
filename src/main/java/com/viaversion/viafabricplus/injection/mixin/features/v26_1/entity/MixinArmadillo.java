package com.viaversion.viafabricplus.injection.mixin.features.v26_1.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Armadillo.class)
public abstract class MixinArmadillo {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/armadillo/Armadillo;isBaby()Z"))
    private boolean dontChangeScale(Armadillo instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

}
