package com.viaversion.viafabricplus.injection.mixin.features.v1_14_3;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    private BlockPos destroyBlockPos;

    @Inject(method = "destroyBlock", at = @At("TAIL"))
    private void resetBlockBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_3)) {
            this.destroyBlockPos = new BlockPos(this.destroyBlockPos.getX(), -1, this.destroyBlockPos.getZ());
        }
    }

}
