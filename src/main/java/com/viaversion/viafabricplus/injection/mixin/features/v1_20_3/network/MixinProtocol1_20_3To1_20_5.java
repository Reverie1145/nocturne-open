package com.viaversion.viafabricplus.injection.mixin.features.v1_20_3.network;

import com.viaversion.viafabricplus.protocoltranslator.util.NotificationUtil;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPacket1_20_3;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ServerboundPacket1_20_3;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ServerboundPackets1_20_3;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.Protocol1_20_3To1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ClientboundPacket1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPacket1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Protocol1_20_3To1_20_5.class, remap = false)
public abstract class MixinProtocol1_20_3To1_20_5 extends AbstractProtocol<ClientboundPacket1_20_3, ClientboundPacket1_20_5, ServerboundPacket1_20_3, ServerboundPacket1_20_5> {

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void removeCommandHandlers(CallbackInfo ci) {

        registerServerbound(ServerboundPackets1_20_5.CHAT, ServerboundPackets1_20_3.CHAT, null, true);
        registerServerbound(ServerboundPackets1_20_5.CHAT_ACK, ServerboundPackets1_20_3.CHAT_ACK, null, true);
        registerServerbound(ServerboundPackets1_20_5.CHAT_SESSION_UPDATE, ServerboundPackets1_20_3.CHAT_SESSION_UPDATE, null, true);

        registerServerbound(ServerboundPackets1_20_5.CHAT_COMMAND_SIGNED, ServerboundPackets1_20_3.CHAT_COMMAND, null, true);

        registerServerbound(ServerboundPackets1_20_5.CHAT_COMMAND, ServerboundPackets1_20_3.CHAT_COMMAND, wrapper -> {
            NotificationUtil.warnIncompatibilityPacket("1.20.5", "CHAT_COMMAND", "ClientPacketListener#sendCommand");
            wrapper.cancel();
        }, true);
    }

}
