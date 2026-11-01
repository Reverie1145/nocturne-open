package com.viaversion.viafabricplus.injection.mixin.features.b1_8;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Unique
    private static final SoundEvent viaFabricPlus$oof_hurt = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("viafabricplus", "oof.hurt"));

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void replaceSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        if (ViaFabricPlusImpl.impl().visuals().replaceHurtSoundWithOOFSound().isActive()) {
            cir.setReturnValue(viaFabricPlus$oof_hurt);
        }
    }

}
