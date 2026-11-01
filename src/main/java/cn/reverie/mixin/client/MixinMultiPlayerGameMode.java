package cn.reverie.mixin.client;

import cn.reverie.manager.PlayerHookManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void nocturne$autoToolStartDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        PlayerHookManager.onAutoToolStartDestroy(pos, direction);
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void nocturne$autoToolContinueDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        PlayerHookManager.onAutoToolContinueDestroy(pos, direction);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
    private void nocturne$autoToolStopDestroy(CallbackInfo ci) {
        PlayerHookManager.onAutoToolStopDestroy();
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void nocturne$autoToolDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        PlayerHookManager.onAutoToolDestroy(pos);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void nocturne$autoToolPreAttack(Player player, Entity entity, CallbackInfo ci) {
        if (player instanceof LocalPlayer localPlayer) {
            cn.reverie.app.event.player.AttackEvent attackEvent = new cn.reverie.app.event.player.AttackEvent(entity);
            cn.reverie.app.event.EventBus.INSTANCE.post(attackEvent);
            if (attackEvent.isCancelled()) {
                ci.cancel();
                return;
            }
            PlayerHookManager.onAutoToolPreAttack(localPlayer, entity);
        }
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void nocturne$autoToolPostAttack(Player player, Entity entity, CallbackInfo ci) {
        if (player instanceof LocalPlayer localPlayer) {
            PlayerHookManager.onAutoToolPostAttack(localPlayer, entity);
        }
    }
}
