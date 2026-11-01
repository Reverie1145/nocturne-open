package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import net.minecraft.network.chat.Component;

public enum PacketTranslationError implements EnumSetting.EnumValue {

    KICK,
    CANCEL_AND_NOTIFY,
    CANCEL;

    final Component component;

    PacketTranslationError() {
        this.component = Component.translatable("base.viafabricplus." + this.name().toLowerCase());
    }

    @Override
    public Component component() {
        return this.component;
    }

}
