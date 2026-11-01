package com.viaversion.viafabricplus.injection.mixin.features.v1_21_5;

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
    protected abstract ClientPacketListener.CommandCheckResult verifyCommand(final String command);

    @Redirect(method = "sendUnattendedCommand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;verifyCommand(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/ClientPacketListener$CommandCheckResult;"))
    private ClientPacketListener.CommandCheckResult dontOpenConfirmationScreens(ClientPacketListener instance, String command) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            final ClientPacketListener.CommandCheckResult result = verifyCommand(command);
            return result == ClientPacketListener.CommandCheckResult.PARSE_ERRORS || result == ClientPacketListener.CommandCheckResult.PERMISSIONS_REQUIRED ? ClientPacketListener.CommandCheckResult.NO_ISSUES : result;
        } else {
            return verifyCommand(command);
        }
    }

}
