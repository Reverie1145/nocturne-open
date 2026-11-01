package com.viaversion.viafabricplus.injection.mixin.features.v1_14_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Redirect(method = "aiStep",
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z")),
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSwimming()Z", ordinal = 0))
    private boolean dontAllowSneakingWhileSwimming(LocalPlayer instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_14_1) && instance.isSwimming();
    }

}
