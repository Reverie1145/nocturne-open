package com.viaversion.viafabricplus.injection.mixin.features.v1_15_1;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onClimbable()Z"))
    private boolean allowElytraWhenClimbing(boolean original) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_15_1) && original;
    }

}
