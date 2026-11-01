package com.viaversion.viafabricplus.injection.mixin.features.v1_21_5.movement;

import cn.reverie.manager.NetworkHookManager;
import cn.reverie.manager.LegacySneakingSender;
import cn.reverie.util.network.PacketUtil;
import com.mojang.authlib.GameProfile;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.Protocol1_21_5To1_21_6;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends AbstractClientPlayer implements LegacySneakingSender {

    @Shadow
    @Final
    protected Minecraft minecraft;

    @Shadow
    public abstract boolean isShiftKeyDown();

    @Unique
    private boolean viaFabricPlus$lastSneaking;

    public MixinLocalPlayer(final ClientLevel level, final GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initLastSneaking(Minecraft minecraft, ClientLevel level, ClientPacketListener connection, StatsCounter stats, ClientRecipeBook recipeBook, Input lastSentInput, boolean wasSprinting, ChatAbilities chatAbilities, CallbackInfo ci) {
        viaFabricPlus$lastSneaking = lastSentInput.shift();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V", shift = At.Shift.AFTER))
    private void sendSneakingPacket(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().betweenInclusive(ProtocolVersion.v1_21_2, ProtocolVersion.v1_21_5)) {
            this.viaFabricPlus$sendSneakingPacket();
        }
    }

    @Inject(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendIsSprintingIfNeeded()V", shift = At.Shift.AFTER))
    private void sendSneakingAfterSprinting(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            this.viaFabricPlus$sendSneakingPacket();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 0))
    private void skipVVProtocol(ClientPacketListener instance, Packet<?> packet) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5) && packet instanceof ServerboundPlayerInputPacket(
            Input i
        )) {

            this.viaFabricPlus$sendInputPacket(i);
        } else {
            instance.send(packet);
        }
    }

    @Unique
    private void viaFabricPlus$sendInputPacket(final Input playerInput) {
        byte flags = 0;
        flags = (byte) (flags | (playerInput.forward() ? 0x1 : 0));
        flags = (byte) (flags | (playerInput.backward() ? 0x2 : 0));
        flags = (byte) (flags | (playerInput.left() ? 0x4 : 0));
        flags = (byte) (flags | (playerInput.right() ? 0x8 : 0));
        flags = (byte) (flags | (playerInput.jump() ? 0x10 : 0));
        flags = (byte) (flags | (playerInput.shift() ? 0x20 : 0));
        flags = (byte) (flags | (playerInput.sprint() ? 0x40 : 0));

        final PacketWrapper inputPacket = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_INPUT, ViaFabricPlus.api().userConnection());
        inputPacket.write(Types.BYTE, flags);
        if (!NetworkHookManager.captureAuraProtocolPacket(inputPacket, Protocol1_21_5To1_21_6.class)) {
            inputPacket.scheduleSendToServer(Protocol1_21_5To1_21_6.class);
        }
    }

    @Override
    public void nocturne$sendLegacySneakingPacket() {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            this.viaFabricPlus$sendSneakingPacket();
        }
    }

    @Unique
    private void viaFabricPlus$sendSneakingPacket() {
        final boolean sneaking = this.isShiftKeyDown();
        if (sneaking == this.viaFabricPlus$lastSneaking) {
            return;
        }

        final PacketWrapper sneakingPacket = PacketWrapper.create(ServerboundPackets1_21_5.PLAYER_COMMAND, ViaFabricPlus.api().userConnection());
        sneakingPacket.write(Types.VAR_INT, PacketUtil.legacyEntityId(ViaFabricPlus.api().userConnection(), getId()));
        sneakingPacket.write(Types.VAR_INT, sneaking ? 0 : 1);
        sneakingPacket.write(Types.VAR_INT, 0);
        if (!NetworkHookManager.captureAuraProtocolPacket(sneakingPacket, Protocol1_21_5To1_21_6.class)) {
            sneakingPacket.scheduleSendToServer(Protocol1_21_5To1_21_6.class);
        }
        this.viaFabricPlus$lastSneaking = sneaking;
    }

}
