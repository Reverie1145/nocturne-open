package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "render", at = @At("HEAD"))
    private void nocturne$updateAimAssistFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        RenderHookManager.updateAimAssistFrame();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void nocturne$onBeforeGuiRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        RenderHookManager.renderBeforeGui(deltaTracker);
    }

    @Inject(
            method = "bobHurt",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onBobHurt(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (RenderHookManager.shouldCancelHurtCam()) {
            ci.cancel();
        }
    }
}
