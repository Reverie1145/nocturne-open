package cn.reverie.mixin.gui;

import cn.reverie.manager.RenderHookManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class MixinHud {

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void nocturne$replaceScoreboardSidebar(GuiGraphicsExtractor graphics, net.minecraft.world.scores.Objective objective, CallbackInfo ci) {
        if (RenderHookManager.shouldReplaceScoreboard()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void nocturne$onExtractHudRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        RenderHookManager.renderHud(graphics, deltaTracker);
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void nocturne$interceptVanillaPotionHud(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        if (RenderHookManager.shouldInterceptVanillaPotionHud()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"
            )
    )
    private int nocturne$spoofHotbarSelection(Inventory inventory) {
        if (RenderHookManager.shouldSpoofHotbar()) {
            return RenderHookManager.spoofedHotbarSlot();
        }
        return inventory.getSelectedSlot();
    }
}
