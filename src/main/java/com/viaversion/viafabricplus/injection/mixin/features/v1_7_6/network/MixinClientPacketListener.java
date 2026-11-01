package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.network;

import com.viaversion.viafabricplus.features.v1_7_6.LegacyTabList;
import com.viaversion.viafabricplus.injection.access.v1_7_6.IPlayerTabOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void initPlayerListFix(ClientboundLoginPacket packet, CallbackInfo ci) {
        LegacyTabList.globalTablistIndex = 0;
        ((IPlayerTabOverlay) Minecraft.getInstance().gui.hud.getTabList()).viaFabricPlus$setMaxPlayers(packet.maxPlayers());
    }

}
