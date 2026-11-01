package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import net.minecraft.network.chat.Component;

public enum ItemFilter implements EnumSetting.EnumValue {

    VANILLA_AND_MODDED,
    VANILLA_ONLY,
    OFF;

    final Component component;

    ItemFilter() {
        this.component = Component.translatable("base.viafabricplus." + this.name().toLowerCase());
    }

    @Override
    public Component component() {
        return this.component;
    }

}
