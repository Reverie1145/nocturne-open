package com.viaversion.viafabricplus.injection.mixin.features.v1_8.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Shadow
    @Final
    private Abilities abilities;

    @Redirect(method = "getAttackStrengthScale", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float removeAttackCooldown(final float value, final float min, final float max) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 1.0F;
        } else {
            return Mth.clamp(value, min, max);
        }
    }

    @Redirect(method = "getItemSwapScale", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float removeSwapCooldown(final float value, final float min, final float max) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 1.0F;
        } else {
            return Mth.clamp(value, min, max);
        }
    }

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void fixCreativeCheck(CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            cir.setReturnValue(this.abilities.instabuild);
        }
    }

}
