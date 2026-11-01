package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import cn.reverie.render.EditBoxAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class MixinEditBox implements EditBoxAccess {
    @Shadow private int displayPos;
    @Shadow private int highlightPos;
    @Shadow private String suggestion;

    @Override
    public int nocturne$getDisplayPos() {
        return displayPos;
    }

    @Override
    public int nocturne$getHighlightPos() {
        return highlightPos;
    }

    @Override
    public String nocturne$getSuggestion() {
        return suggestion;
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void nocturne$replaceChatInputRenderer(CallbackInfo ci) {
        if (GuiHookManager.shouldReplaceChatInput(Minecraft.getInstance().gui.screen(), this)) ci.cancel();
    }
}
