package cn.reverie.mixin.item;

import cn.reverie.manager.PlayerHookManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fuliki's legacy sword-use base, scoped to the sword owned by Aura's block cycle. */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void nocturne$legacySwordUse(Level level, Player player, InteractionHand hand,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND &&
                PlayerHookManager.shouldUseAuraLegacySword((ItemStack) (Object) this, player)) {
            player.startUsingItem(hand);
            cir.setReturnValue(InteractionResult.CONSUME);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void nocturne$legacySwordAnimation(CallbackInfoReturnable<ItemUseAnimation> cir) {
        if (PlayerHookManager.shouldUseAuraLegacySword((ItemStack) (Object) this, null)) {
            cir.setReturnValue(ItemUseAnimation.BLOCK);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void nocturne$legacySwordDuration(LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (PlayerHookManager.shouldUseAuraLegacySword((ItemStack) (Object) this, user)) {
            cir.setReturnValue(72000);
        }
    }
}
