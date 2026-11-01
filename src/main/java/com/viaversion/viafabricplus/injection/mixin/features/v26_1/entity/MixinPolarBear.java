package com.viaversion.viafabricplus.injection.mixin.features.v26_1.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PolarBear.class)
public abstract class MixinPolarBear {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/polarbear/PolarBear;isBaby()Z"))
    private boolean dontChangeScale(PolarBear instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

}
