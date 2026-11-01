package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2;

import com.mojang.authlib.GameProfile;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemotePlayer.class)
public abstract class MixinRemotePlayer extends AbstractClientPlayer {

    public MixinRemotePlayer(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"))
    private void onUpdatePose(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            super.updatePlayerPose();
        }
    }

}
