package com.viaversion.viafabricplus.injection.mixin.features.v1_20;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Redirect(method = "applyPlayerInfoUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGameModeChanged(Lnet/minecraft/world/level/GameType;)V"))
    private void dontResetVelocity(LocalPlayer instance, GameType gameType) {
        if (ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_20)) {
            instance.onGameModeChanged(gameType);
        }
    }

}
