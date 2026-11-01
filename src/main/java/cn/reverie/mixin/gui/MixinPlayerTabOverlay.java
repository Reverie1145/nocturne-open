package cn.reverie.mixin.gui;

import cn.reverie.module.render.NameProtect;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class MixinPlayerTabOverlay {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void nocturne$protectPlayerName(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        String replacement = NameProtect.replacementForPlayer(playerInfo.getProfile().id());
        if (replacement == null) {
            return;
        }

        Component original = cir.getReturnValue();
        String accountName = playerInfo.getProfile().name();
        cir.setReturnValue(NameProtect.protectDisplayName(original, accountName, replacement));
    }
}
