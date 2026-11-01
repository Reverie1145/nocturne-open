package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class MixinLightmapRenderStateExtractor {

    @Shadow
    private boolean needsUpdate;

    @Inject(
            method = "extract",
            at = @At("HEAD")
    )
    private void nocturne$onExtractHead(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (RenderHookManager.needsFullbrightUpdate()) {
            this.needsUpdate = true;
            RenderHookManager.clearFullbrightUpdate();
        }
    }

    @Inject(
            method = "extract",
            at = @At("RETURN")
    )
    private void nocturne$onExtractReturn(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        RenderHookManager.applyFullbright(renderState);
    }
}
