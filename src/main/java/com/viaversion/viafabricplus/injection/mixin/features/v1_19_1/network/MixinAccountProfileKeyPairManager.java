package com.viaversion.viafabricplus.injection.mixin.features.v1_19_1.network;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import com.viaversion.viafabricplus.injection.access.v1_19_1.IProfilePublicKey_Data;
import net.minecraft.client.multiplayer.AccountProfileKeyPairManager;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AccountProfileKeyPairManager.class)
public abstract class MixinAccountProfileKeyPairManager {

    @Inject(method = "parsePublicKey", at = @At("RETURN"))
    private static void trackLegacyKey(KeyPairResponse response, CallbackInfoReturnable<ProfilePublicKey.Data> cir) {
        ((IProfilePublicKey_Data) (Object) cir.getReturnValue()).viafabricplus$setLegacyPublicKeySignature(((IProfilePublicKey_Data) (Object) response).viafabricplus$getLegacyPublicKeySignature());
    }

}
