package com.viaversion.viafabricplus.api.protocoltranslator;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.channel.Channel;
import java.util.function.BiConsumer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import org.jetbrains.annotations.Nullable;

public interface ProtocolTranslation {

    ProtocolVersion targetVersion();

    ProtocolVersion targetVersion(final Connection connection);

    ProtocolVersion targetVersion(final Channel channel);

    default void setTargetVersion(final ProtocolVersion targetVersion) {
        this.setTargetVersion(targetVersion, false);
    }

    void setTargetVersion(final ProtocolVersion targetVersion, final boolean revertOnDisconnect);

    void addChangeProtocolVersionListener(final BiConsumer<ProtocolVersion, ProtocolVersion> listener);

    @Nullable UserConnection userConnection();

    @Nullable UserConnection userConnection(final Connection connection);

    @Nullable ProtocolVersion serverVersion(final ServerData serverData);

}
