package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {

    @Inject(method = "render", at = @At("RETURN"))
    private void nocturne$captureCameraState(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker,
                                             boolean renderBlockOutline, CameraRenderState cameraState,
                                             Matrix4fc frustumMatrix, GpuBufferSlice fogBuffer,
                                             Vector4f fogColor, boolean renderSky, CallbackInfo ci) {
        RenderHookManager.captureCameraState(cameraState);
    }

    @Inject(
        method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
        at = @At("TAIL")
    )
    private void nocturne$renderInWorldLyrics(LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector, boolean bl, CallbackInfo ci) {
        RenderHookManager.renderInWorldLyrics(levelRenderState, submitNodeCollector);
        RenderHookManager.renderBacktrack(levelRenderState, submitNodeCollector);
        RenderHookManager.renderKnockbackDisplace(levelRenderState, submitNodeCollector);
    }
}
