package com.viaversion.viafabricplus.screen.impl.protocol;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;

public enum ProtocolCategory {

    MODERN,
    LEGACY,
    BETA,
    ALPHA,
    CLASSIC,
    APRIL_FOOLS;

    private final Component title;

    ProtocolCategory() {
        this.title = Component.translatable("protocol_selection.viafabricplus." + name().toLowerCase(Locale.ROOT));
    }

    public static ProtocolCategory of(final ProtocolVersion version) {

        if (version.equals(LegacyProtocolVersion.c0_30cpe)) {
            return CLASSIC;
        }

        return switch (version.getVersionType()) {
            case CLASSIC -> CLASSIC;
            case ALPHA_INITIAL, ALPHA_LATER -> ALPHA;
            case BETA_INITIAL, BETA_LATER -> BETA;
            case RELEASE_INITIAL -> LEGACY;
            case RELEASE -> version.olderThan(ProtocolVersion.v1_16) ? LEGACY : MODERN;
            case SPECIAL -> APRIL_FOOLS;
        };
    }

    public Component title() {
        return this.title;
    }

}
