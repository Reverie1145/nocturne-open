package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import cn.reverie.render.core.ResolutionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(ChatScreen.class)
public abstract class MixinChatScreen implements cn.reverie.render.ChatScreenAccess {

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"
            ),
            index = 4
    )
    private int nocturne$replaceChatInputBackground(int color) {
        return GuiHookManager.styleChatBackground(color);
    }

    @Shadow
    public abstract String normalizeChatMessage(String message);

    @Override
    @Accessor("input")
    public abstract EditBox nocturne$getInput();

    @Inject(method = "init", at = @At("TAIL"))
    private void nocturne$scaleCustomChatInput(CallbackInfo ci) {
        EditBox input = this.nocturne$getInput();
        if (!GuiHookManager.shouldReplaceChatInput((ChatScreen) (Object) this, input)) return;

        float scale = ResolutionHelper.getUiScale();
        input.setX(Math.round(4f * scale));
        input.setY(Math.round((ResolutionHelper.getCanvasHeight() - 12f) * scale));
        input.setWidth(Math.max(0, Math.round((ResolutionHelper.getCanvasWidth() - 8f) * scale)));
        input.setHeight(Math.max(1, Math.round(12f * scale)));
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void nocturne$onHandleChatInput(String msg, boolean addToRecent, CallbackInfo ci) {
        String normalized = this.normalizeChatMessage(msg);
        if (normalized.startsWith(".")) {
            if (addToRecent) {
                Minecraft.getInstance().gui.hud.getChat().addRecentChat(normalized);
            }
            GuiHookManager.executeCommand(normalized.substring(1));
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void nocturne$openIrcImageOnDoubleClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (doubleClick && GuiHookManager.onChatImageDoubleClick(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }
}
