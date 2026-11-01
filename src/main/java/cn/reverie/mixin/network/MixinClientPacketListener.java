package cn.reverie.mixin.network;

import cn.reverie.manager.NetworkHookManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void nocturne$checkPositionCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        NetworkHookManager.onPositionCorrection(packet);
    }

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void nocturne$onSendChat(String content, CallbackInfo ci) {
        if (content != null && !content.startsWith(".")) NetworkHookManager.publishAgentChat(content, "sent");
        if (content != null && content.startsWith(".")) {
            ci.cancel();
        }
    }

    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void nocturne$agentCommand(String command, CallbackInfo ci) {
        NetworkHookManager.publishAgentChat("/" + command, "sent");
    }
}
