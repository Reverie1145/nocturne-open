package com.viaversion.viafabricplus.api.settings;

import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.api.settings.impl.AdvancedSettings;
import com.viaversion.viafabricplus.api.settings.impl.GeneralSettings;
import com.viaversion.viafabricplus.api.settings.impl.VisualSettings;
import java.util.List;

public interface Settings {

    GeneralSettings general();

    VisualSettings visual();

    AdvancedSettings advanced();

    SettingGroup register(final String key);

    List<SettingGroup> groups();

}
