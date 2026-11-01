package com.viaversion.viafabricplus.injection.mixin.features.c0_28;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameModeSwitcherScreen.class)
public abstract class MixinGameModeSwitcherScreen extends Screen {

    protected MixinGameModeSwitcherScreen(final Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void disableInClassic(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(LegacyProtocolVersion.c0_28toc0_30)) {
            this.onClose();
        }
    }

}
