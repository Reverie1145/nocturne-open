package com.viaversion.viafabricplus.injection.access.core;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public interface IConnection {

    void viaFabricPlus$setupPreNettyDecryption();

    ProtocolVersion viaFabricPlus$getTargetVersion();

    void viaFabricPlus$setTargetVersion(final ProtocolVersion serverVersion);

    UserConnection viaFabricPlus$getUserConnection();

    void viaFabricPlus$setUserConnection(final UserConnection connection);

}
