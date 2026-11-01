package com.viaversion.viafabricplus.injection.mixin.features.v1_14_4.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoulSandBlock.class)
public abstract class MixinSoulSandBlock extends Block {

    public MixinSoulSandBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected void entityInside(final @NonNull BlockState state, final @NonNull Level world, final @NonNull BlockPos pos, final @NonNull Entity entity, final @NonNull InsideBlockEffectApplier handler, final boolean bl) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.4D, 1, 0.4D));
        }
    }

    @Override
    public float getSpeedFactor() {
        return ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4) ? 1F : super.getSpeedFactor();
    }

}
