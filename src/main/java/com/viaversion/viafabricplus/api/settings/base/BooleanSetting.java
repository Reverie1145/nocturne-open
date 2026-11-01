package com.viaversion.viafabricplus.api.settings.base;

public interface BooleanSetting extends Setting {

    boolean isActive();

    void setActive(boolean value);

    boolean defaultValue();

}
