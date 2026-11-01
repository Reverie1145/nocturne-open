package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.ViaFabricPlusMixinPlugin;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
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

@Mixin(AbstractCauldronBlock.class)
public abstract class MixinAbstractCauldronBlock extends Block {

    @Unique
    private boolean viaFabricPlus$requireOriginalShape;

    @Unique
    private static final VoxelShape viaFabricPlus$collision_shape_r1_12_2 = Shapes.or(
        Shapes.box(0.0, 0.0, 0.0, 1.0, 0.3125, 1.0),
        Shapes.box(0.0, 0.0, 0.0, 0.125, 1.0, 1.0),
        Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.125),
        Shapes.box(0.875, 0.0, 0.0, 1.0, 1.0, 1.0),
        Shapes.box(0.0, 0.0, 0.875, 1.0, 1.0, 1.0)
    );

    public MixinAbstractCauldronBlock(final Properties properties) {
        super(properties);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlusMixinPlugin.MORE_CULLING_PRESENT && viaFabricPlus$requireOriginalShape) {
            viaFabricPlus$requireOriginalShape = false;
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(Shapes.block());
        }
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(final @NonNull BlockState blockState, final @NonNull BlockGetter blockGetter, final @NonNull BlockPos blockPos, final @NonNull CollisionContext collisionContext) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return viaFabricPlus$collision_shape_r1_12_2;
        } else {
            return super.getCollisionShape(blockState, blockGetter, blockPos, collisionContext);
        }
    }

    @Override
    public @NonNull VoxelShape getOcclusionShape(BlockState state) {

        viaFabricPlus$requireOriginalShape = true;
        return super.getOcclusionShape(state);
    }

}
