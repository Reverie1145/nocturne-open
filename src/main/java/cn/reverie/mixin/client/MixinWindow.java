package cn.reverie.mixin.client;

import cn.reverie.manager.RenderHookManager;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class MixinWindow {

    @Shadow public abstract int getWidth();
    @Shadow public abstract int getHeight();
    @Shadow public abstract int getScreenWidth();
    @Shadow public abstract int getScreenHeight();
    @Shadow public abstract int getGuiScaledWidth();
    @Shadow public abstract int getGuiScaledHeight();
    @Shadow public abstract int getGuiScale();

    @Inject(method = "onFramebufferResize", at = @At("TAIL"))
    private void nocturne$onFramebufferResize(long handle, int newWidth, int newHeight, CallbackInfo ci) {
        RenderHookManager.updateResolution((Window) (Object) this, null);
    }

    @Inject(method = "setGuiScale", at = @At("TAIL"))
    private void nocturne$onSetGuiScale(int guiScale, CallbackInfo ci) {
        RenderHookManager.updateResolution((Window) (Object) this, guiScale);
    }
}
