package com.viaversion.viafabricplus.injection.mixin.features.v1_18_2;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.v1_18_2.PendingUpdateManager1_18_2;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @Mutable
    @Shadow
    @Final
    private BlockStatePredictionHandler blockStatePredictionHandler;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void removePendingUpdateManager(CallbackInfo ci) {
        if (ViaFabricPlusImpl.impl().advanced().disableSequencing().isActive()) {
            this.blockStatePredictionHandler = new PendingUpdateManager1_18_2();
        }
    }

}
