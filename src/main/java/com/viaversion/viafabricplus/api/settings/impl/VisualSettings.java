package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;

public interface VisualSettings extends SettingGroup {

    VersionedBooleanSetting hideDownloadTerrainScreenTransitionEffects();

    VersionedBooleanSetting forceUnicodeFontForNonAsciiLanguages();

    VersionedBooleanSetting sneakInstantly();

    VersionedBooleanSetting enableLegacyTablist();

    VersionedBooleanSetting replaceHurtSoundWithOOFSound();

    VersionedBooleanSetting hideModernHUDElements();

    VersionedBooleanSetting replaceCreativeInventory();

    VersionedBooleanSetting oldWalkingAnimation();

    VersionedBooleanSetting legacyPaneOutlines();

    VersionedBooleanSetting legacyCropOutlines();

    VersionedBooleanSetting disableServerPinging();

}
