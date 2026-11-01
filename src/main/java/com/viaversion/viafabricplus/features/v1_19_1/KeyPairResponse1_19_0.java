package com.viaversion.viafabricplus.features.v1_19_1;

import com.mojang.authlib.yggdrasil.response.KeyPairResponse;
import java.nio.ByteBuffer;

public record KeyPairResponse1_19_0(
    KeyPairResponse.KeyPair keyPair,
    ByteBuffer publicKeySignatureV2,
    ByteBuffer publicKeySignature  ,
    String expiresAt,
    String refreshedAfter) {
}
