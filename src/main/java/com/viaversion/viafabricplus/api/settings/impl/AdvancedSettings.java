package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;

public interface AdvancedSettings extends SettingGroup {

    BooleanSetting queueConfigPackets();

    BooleanSetting printNetworkingErrorsToLogs();

    BooleanSetting ignoreFabricSyncErrors();

    BooleanSetting filterNonExistingGlyphs();

    VersionedBooleanSetting removeServerDescriptionSanitize();

    VersionedBooleanSetting dontCreatePacketErrorCrashReports();

    VersionedBooleanSetting disableSequencing();

}
