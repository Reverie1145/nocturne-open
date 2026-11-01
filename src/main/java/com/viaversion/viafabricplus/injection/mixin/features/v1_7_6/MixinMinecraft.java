package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean allowBlockBreakAndItemUsageAtTheSameTime(boolean original) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6) && original;
    }

    @ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean allowItemUsageAndBlockBreakAtTheSameTime(boolean original) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6) && original;
    }

    @WrapOperation(method = "startAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasMissTime()Z", ordinal = 1))
    private boolean removeHitPenalty(MultiPlayerGameMode instance, Operation<Boolean> original) {

        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6) && original.call(instance);
    }

}
