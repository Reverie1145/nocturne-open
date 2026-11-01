package com.viaversion.viafabricplus;

import com.google.common.base.Preconditions;
import com.viaversion.viafabricplus.api.ViaFabricPlusAPI;
import com.viaversion.viafabricplus.api.ViaFabricPlusBase;
import org.jetbrains.annotations.ApiStatus;

public final class ViaFabricPlus {

    private static ViaFabricPlusAPI api;

    @ApiStatus.Internal
    public static void init(final ViaFabricPlusAPI api) {
        Preconditions.checkArgument(ViaFabricPlus.api == null, "ViaFabricPlus has already been initialized!");
        ViaFabricPlus.api = api;
    }

    public static ViaFabricPlusAPI api() {
        Preconditions.checkArgument(api != null, "ViaFabricPlus has not been initialized!");
        return api;
    }

    private static final ViaFabricPlusBase legacy = new ViaFabricPlusBase() {};
    @Deprecated
    public static ViaFabricPlusBase getImpl() {
        return legacy;
    }

}
