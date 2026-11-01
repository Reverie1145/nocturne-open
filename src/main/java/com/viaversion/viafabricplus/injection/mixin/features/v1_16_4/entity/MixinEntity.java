package com.viaversion.viafabricplus.injection.mixin.features.v1_16_4.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Redirect(method = {"setYRot", "setXRot"}, at = @At(value = "INVOKE", target = "Ljava/lang/Float;isFinite(F)Z"))
    private boolean allowInfiniteValues(float f) {
        return Float.isFinite(f) || ((Object) this instanceof LocalPlayer && ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4));
    }

}
