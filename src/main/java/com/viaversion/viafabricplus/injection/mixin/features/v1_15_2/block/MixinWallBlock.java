package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WallBlock.class)
public abstract class MixinWallBlock {

    @Inject(method = "getStateForPlacement", at = @At("RETURN"), cancellable = true)
    private void modifyPlacementState(CallbackInfoReturnable<BlockState> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(viaFabricPlus$oldWallPlacementLogic(cir.getReturnValue()));
        }
    }

    @Inject(method = "updateShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/ScheduledTickAccess;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), cancellable = true)
    private void modifyBlockState(CallbackInfoReturnable<BlockState> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(viaFabricPlus$oldWallPlacementLogic(cir.getReturnValue()));
        }
    }

    @Unique
    private static BlockState viaFabricPlus$oldWallPlacementLogic(BlockState state) {
        boolean addUp = false;
        if (state.getValue(WallBlock.NORTH) == WallSide.TALL) {
            state = state.setValue(WallBlock.NORTH, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.EAST) == WallSide.TALL) {
            state = state.setValue(WallBlock.EAST, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.SOUTH) == WallSide.TALL) {
            state = state.setValue(WallBlock.SOUTH, WallSide.LOW);
            addUp = true;
        }
        if (state.getValue(WallBlock.WEST) == WallSide.TALL) {
            state = state.setValue(WallBlock.WEST, WallSide.LOW);
            addUp = true;
        }
        if (addUp) {
            state = state.setValue(WallBlock.UP, true);
        }
        return state;
    }

}
