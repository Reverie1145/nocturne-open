package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndPortalFrameBlock.class)
public abstract class MixinEndPortalFrameBlock extends Block {

    @Shadow
    @Final
    private static VoxelShape SHAPE_EMPTY;

    @Shadow
    @Final
    public static BooleanProperty HAS_EYE;

    @Unique
    private static final VoxelShape viaFabricPlus$eye_shape_r1_12_2 = Block.box(5.0D, 13.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    @Unique
    private static final VoxelShape viaFabricPlus$frame_with_eye_shape_r1_12_2 = Shapes.or(SHAPE_EMPTY, viaFabricPlus$eye_shape_r1_12_2);

    public MixinEndPortalFrameBlock(Properties settings) {
        super(settings);
    }

    @Inject(method = "getShape", at = @At(value = "HEAD"), cancellable = true)
    private void changeOutlineShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(SHAPE_EMPTY);
        }
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return state.getValue(HAS_EYE) ? viaFabricPlus$frame_with_eye_shape_r1_12_2 : SHAPE_EMPTY;
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
