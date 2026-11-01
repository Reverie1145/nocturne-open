package com.viaversion.viafabricplus.injection.mixin.features.v1_14;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_15_2.Block1_14;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BaseTorchBlock.class, LadderBlock.class, TripWireHookBlock.class})

public abstract class MixinCanPlaceAt1_14 {

    @Inject(method = "canSurvive", at = @At(value = "RETURN"), cancellable = true)
    private void canPlaceAt1_14(BlockState state, LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14)) {

            final Block block = level.getBlockState(pos).getBlock();
            if (Block1_14.isExceptBlockForAttachWithPiston(block)) {
                cir.setReturnValue(false);
            }
        }
    }

}
