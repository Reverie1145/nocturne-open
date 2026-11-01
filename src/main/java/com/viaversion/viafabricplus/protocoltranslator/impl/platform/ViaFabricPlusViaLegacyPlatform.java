package com.viaversion.viafabricplus.protocoltranslator.impl.platform;

import com.viaversion.viaversion.api.Via;
import net.raphimc.vialegacy.ViaLegacyPlatformImpl;

public final class ViaFabricPlusViaLegacyPlatform extends ViaLegacyPlatformImpl {

    @Override
    public String getCpeAppName() {
        return Via.getPlatform().getPlatformName() + " " + Via.getPlatform().getPlatformVersion();
    }

}
