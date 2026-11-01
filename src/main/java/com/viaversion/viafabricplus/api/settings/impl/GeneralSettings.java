package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import com.viaversion.viafabricplus.api.settings.base.SettingGroup;

public interface GeneralSettings extends SettingGroup {

    EnumSetting<Orientation> multiplayerScreenButtonOrientation();

    EnumSetting<Orientation> addServerScreenButtonOrientation();

    EnumSetting<Orientation> directConnectScreenButtonOrientation();

    EnumSetting<ItemFilter> removeNotAvailableItemsFromCreativeTab();

    BooleanSetting saveSelectedProtocolVersion();

    BooleanSetting showAdvertisedServerVersion();

    EnumSetting<PacketTranslationError> ignorePacketTranslationErrors();

    BooleanSetting loadSkinsAndSkullsInLegacyVersions();

    BooleanSetting betaCraftAuthentication();

}
