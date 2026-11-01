package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class MixinGui {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen nocturne$replaceTitleScreenArg(Screen screen) {
        if (screen != null && screen.getClass() == TitleScreen.class) {
            return GuiHookManager.authenticatedMenu();
        }
        return screen;
    }

    @Redirect(method = "setScreen", at = @At(value = "NEW", target = "()Lnet/minecraft/client/gui/screens/TitleScreen;"))
    private TitleScreen nocturne$redirectFallbackTitleScreen() {
        return (TitleScreen) GuiHookManager.authenticatedMenu();
    }
}
