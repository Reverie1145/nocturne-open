package com.viaversion.viafabricplus.injection.mixin.features.v1_8;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ClientLevel.class)
public abstract class MixinClientLevel {

    @ModifyConstant(method = "doAddParticle", constant = @Constant(doubleValue = 1024.0))
    private double lowerParticleRenderDistance(double constant) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return 256.0;
        } else {
            return constant;
        }
    }

}
