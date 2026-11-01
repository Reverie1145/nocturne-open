package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    @ModifyArgs(
            method = "submitHandsWithItems",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
            )
    )
    private void nocturne$modifySubmitArmArgs(Args args) {
        if (RenderHookManager.spoofingItem() && args.get(3) == InteractionHand.MAIN_HAND) {
            args.set(5, RenderHookManager.spoofedItemStack());
            args.set(6, 0.0f);
        } else if (RenderHookManager.shouldCancelDropAnimation() && args.get(3) == InteractionHand.MAIN_HAND) {
            args.set(6, 0.0f);
        }
    }

    @Inject(
            method = "submitArmWithItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onRenderItem(
            AbstractClientPlayer player,
            float frameInterp,
            float xRot,
            InteractionHand hand,
            float attack,
            ItemStack itemStack,
            float inverseArmHeight,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo ci
    ) {
        if (RenderHookManager.renderItem(
                (ItemInHandRenderer) (Object) this,
                player,
                frameInterp,
                xRot,
                hand,
                attack,
                itemStack,
                inverseArmHeight,
                poseStack,
                submitNodeCollector,
                lightCoords
        )) {
            ci.cancel();
        }
    }
}
