package com.viaversion.viafabricplus.injection.mixin.features.v1_19_1.network;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.authlib.yggdrasil.YggdrasilUserApiService;
import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.features.v1_19_1.KeyPairResponse1_19_0;
import com.viaversion.viafabricplus.injection.access.v1_19_1.IProfilePublicKey_Data;
import java.net.URL;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = YggdrasilUserApiService.class, remap = false)
public abstract class MixinYggdrasilUserApiService {

    @Shadow
    @Final
    private MinecraftClient minecraftClient;

    @Shadow
    @Final
    private URL routeKeyPair;

    @Overwrite
    public KeyPairResponse getKeyPair() {
        final KeyPairResponse1_19_0 response = minecraftClient.post(routeKeyPair, KeyPairResponse1_19_0.class);

        if (response == null) {
            return null;
        }

        final KeyPairResponse keyPairResponse = new KeyPairResponse(
            response.keyPair(),
            response.publicKeySignatureV2(),
            response.expiresAt(),
            response.refreshedAfter()
        );

        if (response.publicKeySignature() != null && response.publicKeySignature().array().length != 0) {
            ((IProfilePublicKey_Data) (Object) keyPairResponse).viafabricplus$setLegacyPublicKeySignature(response.publicKeySignature().array());
        } else {
            ViaFabricPlusImpl.impl().logger().error("Could not get legacy public key signature. 1.19.0 with secure-profiles enabled will not work!");
        }

        return keyPairResponse;
    }

}
