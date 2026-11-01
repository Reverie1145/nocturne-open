package com.viaversion.viafabricplus.api.settings.base;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

public interface Setting {

    Component name();

    void write(final JsonObject object);

    void read(final JsonObject object);

}
