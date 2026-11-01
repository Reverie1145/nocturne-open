package com.viaversion.viafabricplus.api.settings.base;

import com.google.gson.JsonObject;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;
import java.util.List;
import net.minecraft.network.chat.Component;

public interface SettingGroup {

    Component name();

    List<Setting> settings();

    BooleanSetting registerBoolean(final String key, final boolean defaultValue);

    <T extends Enum<T> & EnumSetting.EnumValue> EnumSetting<T> registerEnum(final String key, final T defaultValue);

    VersionedBooleanSetting registerVersionedBoolean(final String key, final ProtocolVersionRange versionRange, final boolean defaultValue);

    Setting register(final String key, final Setting setting);

    void write(final JsonObject object);

    void read(final JsonObject object);

}
