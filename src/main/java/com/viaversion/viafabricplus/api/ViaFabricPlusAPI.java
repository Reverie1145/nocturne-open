package com.viaversion.viafabricplus.api;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.api.protocoltranslator.Conversions;
import com.viaversion.viafabricplus.api.protocoltranslator.Limitations;
import com.viaversion.viafabricplus.api.protocoltranslator.ProtocolTranslation;
import com.viaversion.viafabricplus.api.screen.Screens;
import com.viaversion.viafabricplus.api.settings.Settings;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.Logger;

public interface ViaFabricPlusAPI {

    default int apiVersion() {
        return 7;
    }

    String version();

    String implVersion();

    Path path();

    Logger logger();

    Settings settings();

    ProtocolTranslation protocolTranslation();

    default ProtocolVersion targetVersion() {
        return this.protocolTranslation().targetVersion();
    }

    default void setTargetVersion(final ProtocolVersion targetVersion) {
        this.protocolTranslation().setTargetVersion(targetVersion);
    }

    default void addChangeProtocolVersionListener(final BiConsumer<ProtocolVersion, ProtocolVersion> listener) {
        this.protocolTranslation().addChangeProtocolVersionListener(listener);
    }

    default UserConnection userConnection() {
        return this.protocolTranslation().userConnection();
    }

    Conversions conversions();

    Limitations limitations();

    Screens screens();

}
