package com.viaversion.viafabricplus.injection.mixin.features.v1_19_3;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Redirect(method = "updateAutoJump", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;invSqrt(F)F"))
    private float useFastInverseSqrt(float x) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            x = Float.intBitsToFloat(1597463007 - (Float.floatToIntBits(x) >> 1));
            return x * (1.5F - (0.5F * x) * x * x);
        } else {
            return Mth.invSqrt(x);
        }
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendIsSprintingIfNeeded()V"))
    private boolean removeSprintingPacket(LocalPlayer instance) {
        return ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_19_3);
    }

}
