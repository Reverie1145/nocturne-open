package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.network;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Shadow
    private boolean isDestroying;

    @Redirect(method = "stopDestroyBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying:Z", opcode = Opcodes.GETFIELD))
    private boolean fixMiningReset1_7(MultiPlayerGameMode instance) {
        return ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6) || instance.isDestroying();
    }

    @WrapWithCondition(method = "stopDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean preventPacketWhenNotMining1_7(ClientPacketListener instance, Packet<?> packet) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6) || this.isDestroying;
    }

    @WrapWithCondition(method = "stopDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;resetAttackStrengthTicker()V"))
    private boolean preventAttackResetWhenNotMining1_7(LocalPlayer instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6) || this.isDestroying;
    }

}
