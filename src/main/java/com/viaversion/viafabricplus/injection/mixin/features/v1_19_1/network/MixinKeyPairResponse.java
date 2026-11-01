package com.viaversion.viafabricplus.injection.mixin.features.v1_19_1.network;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import com.viaversion.viafabricplus.injection.access.v1_19_1.IProfilePublicKey_Data;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = KeyPairResponse.class, remap = false)
public abstract class MixinKeyPairResponse implements IProfilePublicKey_Data {

    @Unique
    private byte[] viaFabricPlus$legacyKeySignature;

    @Override
    public byte[] viafabricplus$getLegacyPublicKeySignature() {
        return this.viaFabricPlus$legacyKeySignature;
    }

    @Override
    public void viafabricplus$setLegacyPublicKeySignature(byte[] signature) {
        this.viaFabricPlus$legacyKeySignature = signature;
    }

}
