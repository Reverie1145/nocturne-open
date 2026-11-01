package com.viaversion.viafabricplus.settings.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import net.minecraft.network.chat.Component;

public final class EnumSettingImpl<T extends Enum<T> & EnumSetting.EnumValue> extends SettingImpl implements EnumSetting<T> {

    private final T defaultValue;
    private T value;

    public EnumSettingImpl(final String key, final Component component, final T defaultValue) {
        super(key, component);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @Override
    public T value() {
        return this.value;
    }

    @Override
    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public T defaultValue() {
        return this.defaultValue;
    }

    @Override
    public T[] values() {
        return this.defaultValue().getDeclaringClass().getEnumConstants();
    }

    @Override
    public void write(final JsonObject object) {
        object.addProperty(this.key(), this.value().name());
    }

    @Override
    public void read(final JsonObject object) {
        final JsonElement value = this.value(object);
        if (value == null) {
            return;
        }

        for (final T constant : this.defaultValue().getDeclaringClass().getEnumConstants()) {
            if (constant.name().equals(value.getAsString())) {
                this.setValue(constant);
                return;
            }
        }
    }

}
