package com.viaversion.viafabricplus.injection.mixin.features.v26_1.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.wolf.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Wolf.class)
public abstract class MixinWolf {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/wolf/Wolf;isBaby()Z"))
    private boolean dontChangeScale(Wolf instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

}
