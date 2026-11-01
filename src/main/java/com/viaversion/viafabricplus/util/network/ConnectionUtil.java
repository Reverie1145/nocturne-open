package com.viaversion.viafabricplus.util.network;

import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class ConnectionUtil {

    public static void connect(final String address, final ProtocolVersion version) {
        connect(address, address, version);
    }

    public static void connect(final String name, final String address) {
        connect(name, address, null);
    }

    public static void connect(final String name, final String address, final ProtocolVersion version) {
        final ServerAddress serverAddress = ServerAddress.parseString(address);
        final ServerData entry = new ServerData(name, serverAddress.getHost(), ServerData.Type.OTHER);

        if (version != null) {
            ((IServerData) entry).viaFabricPlus$forceVersion(version);
        }
        ConnectScreen.startConnecting(Minecraft.getInstance().gui.screen(), Minecraft.getInstance(), serverAddress, entry, false, null);
    }

}
