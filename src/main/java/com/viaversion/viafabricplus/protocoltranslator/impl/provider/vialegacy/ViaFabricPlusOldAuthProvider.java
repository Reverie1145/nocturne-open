package com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import com.viaversion.viafabricplus.util.ChatUtil;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.protocol.release.r1_2_4_5tor1_3_1_2.provider.OldAuthProvider;

public final class ViaFabricPlusOldAuthProvider extends OldAuthProvider {

    @Override
    public void sendAuthRequest(UserConnection connection, String serverId) {
        try {
            final Minecraft client = Minecraft.getInstance();
            client.services().sessionService().joinServer(client.getUser().getProfileId(), client.getUser().getAccessToken(), serverId);
        } catch (Exception e) {
            connection.getChannel().attr(ProtocolTranslationImpl.MINECRAFT_CONNECTION_ATTRIBUTE_KEY).get().disconnect(ChatUtil.prefixText(Component.translatable("betacraft.viafabricplus.failed_to_verify_session")));
            ViaFabricPlusImpl.impl().logger().error("Error occurred while calling join server to verify session", e);
        }
    }

}
