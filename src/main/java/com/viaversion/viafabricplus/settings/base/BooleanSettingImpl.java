package com.viaversion.viafabricplus.settings.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import net.minecraft.network.chat.Component;

public class BooleanSettingImpl extends SettingImpl implements BooleanSetting {

    private final boolean defaultValue;
    private boolean value;

    public BooleanSettingImpl(final String key, final Component component, final boolean defaultValue) {
        super(key, component);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public boolean isActive() {
        return this.value;
    }

    @Override
    public void setActive(boolean value) {
        this.value = value;
    }

    @Override
    public boolean defaultValue() {
        return this.defaultValue;
    }

    @Override
    public void write(final JsonObject object) {
        object.addProperty(this.key(), this.isActive());
    }

    @Override
    public void read(final JsonObject object) {
        final JsonElement value = this.value(object);
        if (value != null) {
            this.setActive(value.getAsBoolean());
        }
    }

}
