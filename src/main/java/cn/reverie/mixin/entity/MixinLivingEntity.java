package cn.reverie.mixin.entity;

import cn.reverie.manager.RenderHookManager;
import cn.reverie.manager.PlayerHookManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity {

    @org.spongepowered.asm.mixin.injection.Inject(
            method = "getItemBlockingWith", at = @At("HEAD"), cancellable = true
    )
    private void nocturne$legacySwordBlockingItem(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = PlayerHookManager.auraLegacyBlockingItem((LivingEntity) (Object) this);
        if (stack != null) cir.setReturnValue(stack);
    }

    @Redirect(
            method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F")
    )
    private float nocturne$moveFixJumpYaw(LivingEntity instance) {
        if (instance instanceof LocalPlayer) {
            Float yaw = RenderHookManager.moveFixJumpYaw(true);
            if (yaw != null) {
                return yaw;
            }
        }
        return instance.getYRot();
    }

    @org.spongepowered.asm.mixin.injection.Inject(
            method = "getCurrentSwingDuration",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onGetCurrentSwingDuration(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if ((Object) this == net.minecraft.client.Minecraft.getInstance().player) {
            Integer duration = RenderHookManager.swingDuration(true);
            if (duration != null) cir.setReturnValue(duration);
        }
    }
}
