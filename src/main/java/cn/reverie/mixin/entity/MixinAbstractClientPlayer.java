package cn.reverie.mixin.entity;

import cn.reverie.manager.PlayerHookManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void nocturne$lockSprintFov(CallbackInfoReturnable<Float> cir) {
        if (((Object) this) != Minecraft.getInstance().player) {
            return;
        }
        cir.setReturnValue(PlayerHookManager.lockSprintFov(cir.getReturnValueF()));
    }
}
