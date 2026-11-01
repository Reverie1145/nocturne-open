package com.viaversion.viafabricplus.settings.base;

import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersionRange;
import net.minecraft.network.chat.Component;

public final class VersionedBooleanSettingImpl extends BooleanSettingImpl implements VersionedBooleanSetting {

    private final ProtocolVersionRange versionRange;

    public VersionedBooleanSettingImpl(final String key, final Component component, final ProtocolVersionRange versionRange, final boolean defaultValue) {
        super(key, component, defaultValue);
        this.versionRange = versionRange;
    }

    @Override
    public ProtocolVersionRange versionRange() {
        return this.versionRange;
    }

    @Override
    public boolean value() {
        return super.isActive();
    }

    @Override
    public void write(final JsonObject object) {
        object.addProperty(this.key(), this.value());
    }

    @Override
    public boolean isActive() {
        return this.versionRange.contains(ViaFabricPlusImpl.impl().targetVersion()) && super.isActive();
    }

    @Override
    public boolean isActive(final ProtocolVersion version) {
        return this.versionRange.contains(version) && super.isActive();
    }

}
