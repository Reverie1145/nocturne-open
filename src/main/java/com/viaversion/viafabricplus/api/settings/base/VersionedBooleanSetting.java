package com.viaversion.viafabricplus.api.settings.base;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;

public interface VersionedBooleanSetting extends BooleanSetting {

    ProtocolVersionRange versionRange();

    boolean isActive(final ProtocolVersion version);

    boolean value();

}
