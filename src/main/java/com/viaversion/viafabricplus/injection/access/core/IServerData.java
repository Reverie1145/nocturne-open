package com.viaversion.viafabricplus.injection.access.core;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface IServerData {

    ProtocolVersion viaFabricPlus$forcedVersion();

    void viaFabricPlus$forceVersion(final ProtocolVersion version);

    boolean viaFabricPlus$passedDirectConnectScreen();

    void viaFabricPlus$passDirectConnectScreen(final boolean state);

    ProtocolVersion viaFabricPlus$translatingVersion();

    void viaFabricPlus$setTranslatingVersion(final ProtocolVersion version);

}
