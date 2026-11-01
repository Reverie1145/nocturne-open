package com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;
import net.minecraft.network.Connection;

public final class ViaFabricPlusCompressionProvider extends CompressionProvider {

    @Override
    public void handlePlayCompression(final UserConnection user, final int threshold) {
        final Connection connection = user.getChannel().attr(ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY).get();
        connection.setupCompression(threshold, true);
    }

}
