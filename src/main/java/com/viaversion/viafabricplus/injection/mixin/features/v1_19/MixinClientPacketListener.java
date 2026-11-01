package com.viaversion.viafabricplus.injection.mixin.features.v1_19;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Shadow
    protected abstract boolean enforcesSecureChat();

    @Redirect(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;enforcesSecureChat()Z"))
    private boolean removeSecureChatWarning(ClientPacketListener instance) {
        return enforcesSecureChat() || ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19);
    }

}
