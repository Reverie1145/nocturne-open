package com.viaversion.viafabricplus.api.settings.base;

import net.minecraft.network.chat.Component;

public interface EnumSetting<T extends EnumSetting.EnumValue> extends Setting {

    T value();

    void setValue(T value);

    T defaultValue();

    T[] values();

    interface EnumValue {

        Component component();

    }

}
