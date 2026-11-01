package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.storage.ProtocolMetadataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(ClientHandshakePacketListenerImpl.class)
public abstract class MixinClientHandshakePacketListenerImpl {

    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "authenticateServer", at = @At("HEAD"), cancellable = true)
    private void onlyVerifySessionInOnlineMode(String digest, CallbackInfoReturnable<Component> cir) {
        final IConnection mixinClientConnection = (IConnection) connection;
        if (mixinClientConnection.viaFabricPlus$getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {

            if (!mixinClientConnection.viaFabricPlus$getUserConnection().get(ProtocolMetadataStorage.class).isAuthenticate()) {
                cir.setReturnValue(null);
            }
        }
    }

}
