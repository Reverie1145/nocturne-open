package com.viaversion.viafabricplus.injection.mixin.features.v1_20.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isPassenger()Z", ordinal = 0))
    private boolean removeVehicleRequirement(LocalPlayer instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_20) && instance.isPassenger();
    }

}
