package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void nocturne$onInit(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;
        if (self.getClass() == TitleScreen.class) {
            ci.cancel();
            GuiHookManager.finishProtectionSplash();
            Minecraft.getInstance().gui.setScreen(GuiHookManager.authenticatedMenu());
        }
    }
}
