package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2.block;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronBarsBlock.class)
public abstract class MixinIronBarsBlock extends CrossCollisionBlock {

    protected MixinIronBarsBlock(final float postWidth, final float postHeight, final float wallWidth, final float wallHeight, final float collisionHeight, final Properties properties) {
        super(postWidth, postHeight, wallWidth, wallHeight, collisionHeight, properties);
    }

    @Inject(method = "getVisualShape", at = @At("HEAD"), cancellable = true)
    private void useCollisionVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            cir.setReturnValue(this.getCollisionShape(state, level, pos, context));
        }
    }

}
