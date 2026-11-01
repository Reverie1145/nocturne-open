package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CarpetBlock.class)
public abstract class MixinCarpetBlock extends Block {

    @Unique
    private static final VoxelShape viaFabricPlus$shape_r1_7_10 = Block.box(0.0D, -0.00001D  , 0.0D, 16.0D, 0.0D, 16.0D);

    public MixinCarpetBlock(Properties settings) {
        super(settings);
    }

    @Override
    public @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter world, @NonNull BlockPos pos, @NonNull CollisionContext context) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            return viaFabricPlus$shape_r1_7_10;
        } else {
            return super.getCollisionShape(state, world, pos, context);
        }
    }

}
