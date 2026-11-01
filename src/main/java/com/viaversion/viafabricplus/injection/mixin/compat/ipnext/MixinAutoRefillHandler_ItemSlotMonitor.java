package com.viaversion.viafabricplus.injection.mixin.compat.ipnext;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("UnresolvedMixinReference")
@Pseudo
@Mixin(targets = "org.anti_ad.mc.ipnext.event.AutoRefillHandler$ItemSlotMonitor", remap = false)
public abstract class MixinAutoRefillHandler_ItemSlotMonitor {

    @Shadow
    public int currentSlotId;

    @Inject(method = {"checkHandle", "checkShouldHandle"}, at = @At("HEAD"), cancellable = true)
    public void dontHandleOffhandSlot(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            if (currentSlotId == 45) ci.cancel();
        }
    }

    @Inject(method = "updateCurrent", at = @At(value = "FIELD", target = "Lorg/anti_ad/mc/ipnext/event/AutoRefillHandler$ItemSlotMonitor;currentSlotId:I", shift = At.Shift.AFTER), cancellable = true)
    public void dontUpdateCurrentOffhandSlot(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            if (currentSlotId == 45) ci.cancel();
        }
    }

}
