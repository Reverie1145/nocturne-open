package com.viaversion.viafabricplus.injection.mixin.features.v1_17_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Options.class)
public abstract class MixinOptions {

    @ModifyVariable(method = "setServerRenderDistance", at = @At("HEAD"), argsOnly = true)
    private int changeServerViewDistance(int serverRenderDistance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_17_1)) {
            return 0;
        } else {
            return serverRenderDistance;
        }
    }

}
