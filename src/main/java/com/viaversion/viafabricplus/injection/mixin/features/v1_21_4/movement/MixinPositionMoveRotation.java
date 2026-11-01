package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PositionMoveRotation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PositionMoveRotation.class)
public abstract class MixinPositionMoveRotation {

    @Redirect(method = "calculateAbsolute", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private static float uncapPlayerPitchMovement(float value, float min, float max) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return value;
        } else {
            return Mth.clamp(value, min, max);
        }
    }

}
