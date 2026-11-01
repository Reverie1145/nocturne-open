package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.protocoltranslator.impl.ViaFabricPlusMappingDataLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPlaceContext.class)
public abstract class MixinBlockPlaceContext extends UseOnContext {

    public MixinBlockPlaceContext(Player player, InteractionHand hand, BlockHitResult hit) {
        super(player, hand, hit);
    }

    @Inject(method = "getNearestLookingDirection", at = @At("HEAD"), cancellable = true)
    private void getPlayerLookDirection1_12_2(CallbackInfoReturnable<Direction> cir) {
        final BlockPlaceContext self = (BlockPlaceContext) (Object) this;
        final Player player = self.getPlayer();

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            final BlockPos placementPos = self.getClickedPos();
            final double blockPosCenterFactor = ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_10) ? 0.5 : 0;

            if (Math.abs(player.getX() - (placementPos.getX() + blockPosCenterFactor)) < 2 && Math.abs(player.getZ() - (placementPos.getZ() + blockPosCenterFactor)) < 2) {
                final double eyeY = player.getY() + player.getEyeHeight(player.getPose());

                if (eyeY - placementPos.getY() > 2) {
                    cir.setReturnValue(Direction.DOWN);
                    return;
                }

                if (placementPos.getY() - eyeY > 0) {
                    cir.setReturnValue(Direction.UP);
                    return;
                }
            }

            cir.setReturnValue(player.getDirection());
        }
    }

    @Inject(method = "canPlace", at = @At("RETURN"), cancellable = true)
    private void canPlace1_12_2(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            cir.setReturnValue(ViaFabricPlusMappingDataLoader.getBlockMaterial(this.getLevel().getBlockState(this.getClickedPos()).getBlock()).equals("decoration") && Block.byItem(this.getItemInHand().getItem()).equals(Blocks.ANVIL));
        }
    }

}
