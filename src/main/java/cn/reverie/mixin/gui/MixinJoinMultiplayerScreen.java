package cn.reverie.mixin.gui;

import cn.reverie.manager.GuiHookManager;
import cn.reverie.render.ui.NvgScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen implements NvgScreen {

    @Override
    public void renderNvg(final float mouseX, final float mouseY, final float partialTicks) {
        GuiHookManager.renderVanillaList((Screen) (Object) this, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return GuiHookManager.vanillaListMouseClicked((Screen) (Object) this, event, doubleClick);
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent event) {
        return GuiHookManager.vanillaListMouseReleased((Screen) (Object) this, event);
    }

    @Override
    public boolean onMouseDragged(MouseButtonEvent event, double dx, double dy) {
        return GuiHookManager.vanillaListMouseDragged((Screen) (Object) this, event, dx, dy);
    }

    @Override
    public boolean onMouseScrolled(double x, double y, double scrollX, double scrollY) {
        return GuiHookManager.vanillaListMouseScrolled((Screen) (Object) this, x, y, scrollX, scrollY);
    }

    @Override
    public boolean onKeyPressed(KeyEvent event) {
        return GuiHookManager.vanillaListKeyPressed((Screen) (Object) this, event);
    }

    @Override
    public boolean onCharTyped(CharacterEvent event) {
        return GuiHookManager.vanillaListCharTyped((Screen) (Object) this, event);
    }
}
