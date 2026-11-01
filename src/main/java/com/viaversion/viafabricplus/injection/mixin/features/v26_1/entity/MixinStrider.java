package com.viaversion.viafabricplus.injection.mixin.features.v26_1.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.monster.Strider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Strider.class)
public abstract class MixinStrider {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Strider;isBaby()Z"))
    private boolean dontChangeScale(Strider instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v26_1) && instance.isBaby();
    }

}
