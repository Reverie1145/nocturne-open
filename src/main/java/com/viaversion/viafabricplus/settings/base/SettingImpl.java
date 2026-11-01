package com.viaversion.viafabricplus.settings.base;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.api.settings.base.Setting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class SettingImpl implements Setting {

    private final String key;
    private final Component component;

    public SettingImpl(final String key, final Component component) {
        this.key = key;
        this.component = component;
    }

    protected String key() {
        return this.key;
    }

    protected @Nullable JsonElement value(final JsonObject object) {
        return object.get(this.key);
    }

    @Override
    public Component name() {
        return this.component;
    }

}
