package com.viaversion.viafabricplus.util;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;

public final class ModMenuScreenFactory implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return screen -> ViaFabricPlusImpl.impl().screens().viaFabricPlusScreen().get(screen);
    }

}
