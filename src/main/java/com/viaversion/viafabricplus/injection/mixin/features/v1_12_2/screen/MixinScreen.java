package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.screen;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Inject(method = "addRenderableWidget", at = @At("HEAD"), cancellable = true)
    private <T extends GuiEventListener & Renderable & NarratableEntry> void removeRecipeBook(T widget, CallbackInfoReturnable<T> cir) {
        if (widget instanceof ImageButton button && button.sprites == RecipeBookComponent.RECIPE_BUTTON_SPRITES) {
            final boolean furnace = ((Screen) (Object) this) instanceof FurnaceScreen;

            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && furnace) {
                cir.setReturnValue(widget);
            }
        }
    }

}
