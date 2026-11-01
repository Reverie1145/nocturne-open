package cn.reverie.mixin.render;

import cn.reverie.manager.RenderHookManager;
import cn.reverie.module.render.NameProtect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nocturne$overrideLocalRotations(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (entity != Minecraft.getInstance().player) {
            return;
        }

        RenderHookManager.applyLocalLivingRotations(entity, state, partialTicks);
        String replacement = NameProtect.replacementFor(entity);
        if (replacement != null && state.nameTag != null) {
            state.nameTag = NameProtect.protectedNameComponent(replacement, state.nameTag.getStyle());
        }
    }
}
