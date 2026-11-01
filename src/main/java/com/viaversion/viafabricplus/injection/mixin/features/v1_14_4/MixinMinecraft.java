package com.viaversion.viafabricplus.injection.mixin.features.v1_14_4;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;swingSource()Lnet/minecraft/world/InteractionResult$SwingSource;", ordinal = 0))
    private InteractionResult.SwingSource disableSwing(InteractionResult.Success instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return InteractionResult.SwingSource.NONE;
        } else {
            return instance.swingSource();
        }
    }

    @Redirect(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionResult$Success;swingSource()Lnet/minecraft/world/InteractionResult$SwingSource;", ordinal = 2))
    private InteractionResult.SwingSource disableSwing2(InteractionResult.Success instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return InteractionResult.SwingSource.NONE;
        } else {
            return instance.swingSource();
        }
    }

}
