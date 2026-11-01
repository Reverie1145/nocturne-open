package com.viaversion.viafabricplus.protocoltranslator.netty;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.impl.PacketTranslationError;
import com.viaversion.viafabricplus.util.ChatUtil;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.exception.CancelCodecException;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ViaFabricPlusDecoder extends ViaDecodeHandler {

    public ViaFabricPlusDecoder(UserConnection connection) {
        super(connection);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            super.channelRead(ctx, msg);
        } catch (Throwable e) {
            if (e instanceof CancelCodecException) {
                return;
            }

            final PacketTranslationError mode = ViaFabricPlusImpl.impl().options().ignorePacketTranslationErrors().value();
            if (mode != PacketTranslationError.KICK) {
                ViaFabricPlusImpl.impl().logger().error("Error occurred while decoding packet in ViaFabricPlus decoder", e);
                if (mode == PacketTranslationError.CANCEL_AND_NOTIFY) {
                    ChatUtil.sendPrefixedMessage(Component.translatable("translation.viafabricplus.packet_error").withStyle(ChatFormatting.RED));
                }
            } else {
                throw e;
            }
        }
    }

}
