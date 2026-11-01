package com.viaversion.viafabricplus.injection.mixin.core.integration;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.connection.ConnectionDetails;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    public abstract Connection getConnection();

    @Inject(method = "handleLogin", at = @At("RETURN"))
    public void sendConnectionDetails(ClientboundLoginPacket packet, CallbackInfo ci) {
        final UserConnection user = ((IConnection) getConnection()).viaFabricPlus$getUserConnection();
        if (user != null) {
            ConnectionDetails.sendConnectionDetails(user, ConnectionDetails.MOD_CHANNEL);
        }
    }

}
