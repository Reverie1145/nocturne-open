package com.viaversion.viafabricplus.injection.mixin.features.b1_5;

import com.viaversion.viafabricplus.ViaFabricPlus;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    private Optional<BlockPos> lastClimbablePos;

    @Shadow
    protected abstract boolean trapdoorUsableAsLadder(final BlockPos pos, final BlockState state);

    public MixinLivingEntity(final EntityType<?> type, final Level world) {
        super(type, world);
    }

    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void allowGappedLadderClimb(CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThan(LegacyProtocolVersion.b1_5tob1_5_2) && !cir.getReturnValueZ() && !this.isSpectator()) {
            final BlockPos blockPos = this.blockPosition().above();
            final BlockState blockState = this.level().getBlockState(blockPos);
            if (blockState.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockPos);
                cir.setReturnValue(true);
            } else if (blockState.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockPos, blockState)) {
                this.lastClimbablePos = Optional.of(blockPos);
                cir.setReturnValue(true);
            }
        }
    }

}
