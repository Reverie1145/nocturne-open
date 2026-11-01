package cn.reverie.mixin.gui;

import cn.reverie.manager.RenderHookManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), cancellable = true)
    private void nocturne$onScreenRenderHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        boolean handled = RenderHookManager.renderScreenPre(screen, mouseX, mouseY, a);
        if (handled) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void nocturne$onScreenRenderPost(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        RenderHookManager.renderScreenPost(screen, graphics, mouseX, mouseY, a);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void nocturne$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen) (Object) this;
        if (RenderHookManager.onKeyPressed(screen, event.key(), event.scancode(), event.modifiers())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void nocturne$onClose(CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        RenderHookManager.onScreenClosed(screen);
    }
}
