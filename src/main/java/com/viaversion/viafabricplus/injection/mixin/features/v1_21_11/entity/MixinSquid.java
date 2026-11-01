package com.viaversion.viafabricplus.injection.mixin.features.v1_21_11.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.animal.squid.Squid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Squid.class)
public abstract class MixinSquid {

    @Redirect(method = "getDefaultDimensions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/squid/Squid;isBaby()Z"))
    private boolean dontChangeScale(Squid instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_21_11) && instance.isBaby();
    }

}
