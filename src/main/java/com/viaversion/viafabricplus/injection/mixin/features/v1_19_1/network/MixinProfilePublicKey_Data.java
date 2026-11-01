package com.viaversion.viafabricplus.injection.mixin.features.v1_19_1.network;

import com.viaversion.viafabricplus.injection.access.v1_19_1.IProfilePublicKey_Data;
import net.minecraft.world.entity.player.ProfilePublicKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ProfilePublicKey.Data.class)
public abstract class MixinProfilePublicKey_Data implements IProfilePublicKey_Data {

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
