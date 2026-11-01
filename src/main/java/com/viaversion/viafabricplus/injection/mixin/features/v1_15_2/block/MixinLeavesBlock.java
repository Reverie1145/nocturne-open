package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LeavesBlock.class)
public abstract class MixinLeavesBlock extends Block {

    public MixinLeavesBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getBlockSupportShape", at = @At("HEAD"), cancellable = true)
    private void changeSidesShape(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().betweenInclusive(ProtocolVersion.v1_14, ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(super.getBlockSupportShape(state, level, pos));
        }
    }

}
