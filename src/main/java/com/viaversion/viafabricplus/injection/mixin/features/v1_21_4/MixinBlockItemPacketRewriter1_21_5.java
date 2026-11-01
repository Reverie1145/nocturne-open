package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4;

import com.viaversion.viafabricplus.protocoltranslator.util.NotificationUtil;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.Protocol1_21_4To1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPacket1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.rewriter.BlockItemPacketRewriter1_21_5;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ClientboundPacket1_21_2;
import com.viaversion.viaversion.rewriter.StructuredItemRewriter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockItemPacketRewriter1_21_5.class, remap = false)
public abstract class MixinBlockItemPacketRewriter1_21_5 extends StructuredItemRewriter<ClientboundPacket1_21_2, ServerboundPacket1_21_5, Protocol1_21_4To1_21_5> {

    public MixinBlockItemPacketRewriter1_21_5(final Protocol1_21_4To1_21_5 protocol) {
        super(protocol);
    }

    @Inject(method = "registerPackets", at = @At("RETURN"))
    private void removeContainerClickHandler(CallbackInfo ci) {

        protocol.registerServerbound(ServerboundPackets1_21_5.CONTAINER_CLICK, ServerboundPackets1_21_4.CONTAINER_CLICK, wrapper -> {
            NotificationUtil.warnIncompatibilityPacket("1.21.5", "CONTAINER_CLICK", "MultiPlayerGameMode#handleInventoryMouseClick");
            wrapper.cancel();
        }, true);
    }

}
