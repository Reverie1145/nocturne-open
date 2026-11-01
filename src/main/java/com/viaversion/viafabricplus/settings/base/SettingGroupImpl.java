package com.viaversion.viafabricplus.settings.base;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import com.viaversion.viafabricplus.api.settings.base.Setting;
import com.viaversion.viafabricplus.api.settings.base.SettingGroup;
import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public class SettingGroupImpl implements SettingGroup {

    private final List<Setting> settings = new ArrayList<>();
    private final String key;
    private final Component component;

    public SettingGroupImpl(final String key) {
        this.key = key;
        this.component = Component.translatable(key + "_settings.viafabricplus");
    }

    @Override
    public BooleanSetting registerBoolean(final String key, final boolean defaultValue) {
        final BooleanSetting setting = new BooleanSettingImpl(key, this.settingsName(key), defaultValue);
        this.settings.add(setting);
        return setting;
    }

    @Override
    public <T extends Enum<T> & EnumSetting.EnumValue> EnumSetting<T> registerEnum(final String key, final T defaultValue) {
        final EnumSetting<T> setting = new EnumSettingImpl<>(key, this.settingsName(key), defaultValue);
        this.settings.add(setting);
        return setting;
    }

    @Override
    public VersionedBooleanSetting registerVersionedBoolean(final String key, final ProtocolVersionRange versionRange, final boolean defaultValue) {
        final VersionedBooleanSetting setting = new VersionedBooleanSettingImpl(key, this.settingsName(key), versionRange, defaultValue);
        this.settings.add(setting);
        return setting;
    }

    @Override
    public Setting register(final String key, final Setting setting) {
        this.settings.add(setting);
        return setting;
    }

    private Component settingsName(final String key) {
        return Component.translatable(this.key + "_settings.viafabricplus." + key);
    }

    @Override
    public void write(final JsonObject object) {
        final JsonObject settings = new JsonObject();
        for (final Setting setting : this.settings) {
            setting.write(settings);
        }
        object.add(this.key, settings);
    }

    @Override
    public void read(final JsonObject object) {
        final JsonObject settings = object.getAsJsonObject(this.key);
        if (settings != null) {
            for (final Setting setting : this.settings) {
                setting.read(settings);
            }
        }
    }

    @Override
    public Component name() {
        return this.component;
    }

    @Override
    public List<Setting> settings() {
        return new ArrayList<>(this.settings);
    }

}
