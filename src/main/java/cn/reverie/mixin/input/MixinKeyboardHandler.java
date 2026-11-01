package cn.reverie.mixin.input;

import cn.reverie.manager.InputHookManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void nocturne$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            Minecraft mc = Minecraft.getInstance();

            if (mc.level != null && mc.gui.screen() == null) {
            InputHookManager.onKey(event.key());
            }
        }

        if (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT) {
            Minecraft mc = Minecraft.getInstance();
        if (cn.reverie.manager.GuiHookManager.onNvgKeyPressed(mc.gui.screen(), event)) {
            ci.cancel();
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void nocturne$onCharTyped(long handle, CharacterEvent event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (cn.reverie.manager.GuiHookManager.onNvgCharTyped(mc.gui.screen(), event)) {
            ci.cancel();
        }
    }
}
