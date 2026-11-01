package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Inject(method = "clickCommandAction", at = @At("HEAD"), cancellable = true)
    private static void changeCommandHandling(LocalPlayer player, String command, Screen screenAfterCommand, CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_21_4)) {
            return;
        }

        if (!command.startsWith("/")) {
            ci.cancel();
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19)) {
                player.connection.sendChat(command);
            }
        }
    }

}
