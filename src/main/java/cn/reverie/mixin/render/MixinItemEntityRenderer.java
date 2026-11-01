package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import cn.reverie.render.entity.PhysicsItemRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer {

    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void nocturne$extractPhysicsState(
        ItemEntity entity,
        ItemEntityRenderState state,
        float partialTicks,
        CallbackInfo ci
    ) {
        if (state instanceof PhysicsItemRenderState physics) {
            physics.nocturne$setOnGround(entity.onGround());
            physics.nocturne$setInWater(entity.isInWater());
            physics.nocturne$setInLava(entity.isInLava());
            Vec3 deltaMovement = entity.getDeltaMovement();
            if (deltaMovement != null) {
                physics.nocturne$setMotion(deltaMovement.x, deltaMovement.y, deltaMovement.z);
            }
            physics.nocturne$setYRot(entity.getYRot());
            physics.nocturne$setEntityId(entity.getId());
        }
        RenderHookManager.processPhysicsItemRenderState(state);
    }

    @Inject(
        method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void nocturne$onSubmitItem(
        ItemEntityRenderState state,
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        CameraRenderState camera,
        CallbackInfo ci
    ) {
        if (RenderHookManager.renderPhysicsItem(state, poseStack, submitNodeCollector, camera)) {
            ci.cancel();
        }
    }
}
