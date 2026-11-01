package com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.viafabricplus.injection.access.core.IConnection;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;

public final class ViaFabricPlusEncryptionProvider extends EncryptionProvider {

    @Override
    public void enableDecryption(UserConnection connection) {
        ((IConnection) connection.getChannel().attr(ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY).get()).viaFabricPlus$setupPreNettyDecryption();
    }

}
