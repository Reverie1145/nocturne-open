package com.viaversion.viafabricplus.injection.mixin.features.v1_14;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_15_2.Block1_14;
import com.viaversion.viafabricplus.injection.access.v1_14.ICrossCollisionBlock;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FenceBlock.class)
public abstract class MixinFenceBlock implements ICrossCollisionBlock {

    @Inject(method = "connectsTo", at = @At("RETURN"), cancellable = true)
    private void canConnect1_14(BlockState state, boolean faceSolid, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14)) {
            if (!Block1_14.isExceptBlockForAttachWithPiston(state.getBlock())) {
                cir.setReturnValue(false);
            }
        }
    }

}
