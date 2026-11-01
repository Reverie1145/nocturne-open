package com.viaversion.viafabricplus.injection.mixin.features.v1_21_11.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.goat.Goat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Goat.class)
public abstract class MixinGoat {

    @Redirect(method = "getAgeScale", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/goat/Goat;isBaby()Z"))
    private boolean dontChangeScale(Goat instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

}
