package com.viaversion.viafabricplus.injection.mixin.features.v1_21_11;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.multiplayer.ServerStatusPinger$1")
public abstract class MixinServerStatusPinger_1 {

    @Final
    @Shadow
    ServerData val$data;

    @Shadow
    private static Component sanitizeDescription(final Component original) {
        return null;
    }

    @Redirect(method = "handleStatusResponse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerStatusPinger$1;sanitizeDescription(Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;"))
    private Component removeSanitizeDescription(Component component) {
        if (ViaFabricPlusImpl.impl().advanced().removeServerDescriptionSanitize().isActive(((IServerData) val$data).viaFabricPlus$translatingVersion())) {
            return component;
        } else {
            return sanitizeDescription(component);
        }
    }

}
