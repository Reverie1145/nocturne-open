package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.block.Blocks;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(method = {"lambda$startDestroyBlock$0", "lambda$continueDestroyBlock$0"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean checkFireBlock(MultiPlayerGameMode instance, BlockPos pos, @Local(argsOnly = true) Direction direction) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            return !this.viaFabricPlus$extinguishFire(pos, direction) && instance.destroyBlock(pos);
        } else {
            return instance.destroyBlock(pos);
        }
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void removeClickActions(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_5tob1_5_2) && !containerInput.equals(ContainerInput.PICKUP)) {
            ci.cancel();
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_4_6tor1_4_7) && !containerInput.equals(ContainerInput.PICKUP) && !containerInput.equals(ContainerInput.QUICK_MOVE) && !containerInput.equals(ContainerInput.SWAP) && !containerInput.equals(ContainerInput.CLONE)) {
            ci.cancel();
        }
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2) && containerInput == ContainerInput.SWAP && buttonNum == 40) {
            ci.cancel();
        }
    }

    @Unique
    private boolean viaFabricPlus$extinguishFire(BlockPos blockPos, final Direction direction) {
        blockPos = blockPos.relative(direction);
        if (this.minecraft.level.getBlockState(blockPos).getBlock() == Blocks.FIRE) {
            this.minecraft.level.levelEvent(this.minecraft.player, 1009, blockPos, 0);
            this.minecraft.level.removeBlock(blockPos, false);
            return true;
        }
        return false;
    }

}
