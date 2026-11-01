package com.viaversion.viafabricplus.injection.mixin.features.v1_0_0;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.world.entity.LivingEntity;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @Shadow
    private int noJumpDelay;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void removeJumpDelay(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThan(LegacyProtocolVersion.r1_0_0tor1_0_1)) {
            this.noJumpDelay = 0;
        }
    }

}
