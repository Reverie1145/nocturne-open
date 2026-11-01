package cn.reverie.mixin.input;

import cn.reverie.manager.GuiHookManager;
import cn.reverie.manager.RenderHookManager;
import cn.reverie.render.core.ResolutionHelper;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @Shadow private Minecraft minecraft;

    @Shadow public abstract double getScaledXPos(Window window);
    @Shadow public abstract double getScaledYPos(Window window);

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void nocturne$onHandleMovement(CallbackInfo ci) {
        if (!this.minecraft.isWindowActive()) return;

        Screen screen = this.minecraft.gui.screen();
        if (screen == null) return;

        Window window = this.minecraft.getWindow();
        double xm = getScaledXPos(window);
        double ym = getScaledYPos(window);

        RenderHookManager.onMouseMoved(screen, xm, ym);
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void nocturne$onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (this.minecraft == null) return;
        Screen screen = this.minecraft.gui.screen();
        MouseButtonEvent event = new MouseButtonEvent(getScaledXPos(this.minecraft.getWindow()), getScaledYPos(this.minecraft.getWindow()), rawButtonInfo);
        if (GuiHookManager.publishMouseInput(event, action, screen)) {
            ci.cancel();
            return;
        }
        if (GuiHookManager.isNvgScreen(screen)) {
            Window window = this.minecraft.getWindow();
            double xm = getScaledXPos(window);
            double ym = getScaledYPos(window);
            MouseButtonEvent buttonEvent = new MouseButtonEvent(
                ResolutionHelper.toCanvasX(xm), ResolutionHelper.toCanvasY(ym), rawButtonInfo
            );
            if (action == 1) { // GLFW_PRESS
                if (GuiHookManager.onNvgMouseClicked(screen, buttonEvent, false)) {
                    ci.cancel();
                }
            } else if (action == 0) { // GLFW_RELEASE
                if (GuiHookManager.onNvgMouseReleased(screen, buttonEvent)) {
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void nocturne$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (this.minecraft == null) return;
        Screen screen = this.minecraft.gui.screen();
        if (GuiHookManager.isNvgScreen(screen)) {
            Window window = this.minecraft.getWindow();
            double xm = getScaledXPos(window);
            double ym = getScaledYPos(window);
            boolean discreteScroll = this.minecraft.options.discreteMouseScroll().get();
            double scrollSensitivity = this.minecraft.options.mouseWheelSensitivity().get();
            double scaledXOffset = (discreteScroll ? Math.signum(xoffset) : xoffset) * scrollSensitivity;
            double scaledYOffset = (discreteScroll ? Math.signum(yoffset) : yoffset) * scrollSensitivity;
            if (GuiHookManager.onNvgMouseScrolled(
                screen, ResolutionHelper.toCanvasX(xm), ResolutionHelper.toCanvasY(ym), scaledXOffset, scaledYOffset
            )) {
                ci.cancel();
            }
        }
    }
}
