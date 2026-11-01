package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonHeadBlock.class)
public abstract class MixinPistonHeadBlock extends DirectionalBlock {

    @Unique
    private boolean viaFabricPlus$selfInflicted = false;

    @Unique
    private static final VoxelShape viaFabricPlus$east_head_shape = Block.box(12.0, 0.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$west_head_shape = Block.box(0.0, 0.0, 0.0, 4.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$south_head_shape = Block.box(0.0, 0.0, 12.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$north_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 4.0);

    @Unique
    private static final VoxelShape viaFabricPlus$up_head_shape = Block.box(0.0, 12.0, 0.0, 16.0, 16.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$down_head_shape = Block.box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$up_arm_shape_r1_8_x = Block.box(6.0, 0.0, 6.0, 10.0, 12.0, 10.0);

    @Unique
    private static final VoxelShape viaFabricPlus$down_arm_shape_r1_8_x = Block.box(6.0, 4.0, 6.0, 10.0, 16.0, 10.0);

    @Unique
    private static final VoxelShape viaFabricPlus$south_arm_shape_r1_8_x = Block.box(4.0, 6.0, 0.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape viaFabricPlus$north_arm_shape_r1_8_x = Block.box(4.0, 6.0, 4.0, 12.0, 10.0, 16.0);

    @Unique
    private static final VoxelShape viaFabricPlus$east_arm_shape_r1_8_x = Block.box(0.0, 6.0, 4.0, 12.0, 10.0, 12.0);

    @Unique
    private static final VoxelShape viaFabricPlus$west_arm_shape_r1_8_x = Block.box(6.0, 4.0, 4.0, 10.0, 12.0, 16.0);

    protected MixinPistonHeadBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (viaFabricPlus$selfInflicted) {
            viaFabricPlus$selfInflicted = false;
            return;
        }

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> viaFabricPlus$down_head_shape;
                case UP -> viaFabricPlus$up_head_shape;
                case NORTH -> viaFabricPlus$north_head_shape;
                case SOUTH -> viaFabricPlus$south_head_shape;
                case WEST -> viaFabricPlus$west_head_shape;
                case EAST -> viaFabricPlus$east_head_shape;
            });
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return switch (state.getValue(PistonHeadBlock.FACING)) {
                case DOWN -> Shapes.or(viaFabricPlus$down_head_shape, viaFabricPlus$down_arm_shape_r1_8_x);
                case UP -> Shapes.or(viaFabricPlus$up_head_shape, viaFabricPlus$up_arm_shape_r1_8_x);
                case NORTH -> Shapes.or(viaFabricPlus$north_head_shape, viaFabricPlus$north_arm_shape_r1_8_x);
                case SOUTH -> Shapes.or(viaFabricPlus$south_head_shape, viaFabricPlus$south_arm_shape_r1_8_x);
                case WEST -> Shapes.or(viaFabricPlus$west_head_shape, viaFabricPlus$west_arm_shape_r1_8_x);
                case EAST -> Shapes.or(viaFabricPlus$east_head_shape, viaFabricPlus$east_arm_shape_r1_8_x);
            };
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {

            viaFabricPlus$selfInflicted = true;
            return getShape(state, world, pos, context);
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
