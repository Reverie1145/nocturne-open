package com.viaversion.viafabricplus.api.settings.impl;

import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public enum Orientation implements EnumSetting.EnumValue {

    NONE((_, _, _) -> {
    }),
    LEFT_TOP((button, _, _) -> button.setPosition(5, 5)),
    RIGHT_TOP((button, width, _) -> button.setPosition(width - 98 - 5, 5)),
    LEFT_BOTTOM((button, _, height) -> button.setPosition(5, height - 20 - 5)),
    RIGHT_BOTTOM((button, width, height) -> button.setPosition(width - 98 - 5, height - 20 - 5));

    final Component component;
    final Positioner positioner;

    Orientation(final Positioner positioner) {
        this.component = Component.translatable("base.viafabricplus." + this.name().toLowerCase());
        this.positioner = positioner;
    }

    public Positioner getPositioner() {
        return this.positioner;
    }

    @Override
    public Component component() {
        return this.component;
    }

    @FunctionalInterface
    public interface Positioner {

        void setPosition(final Button button, final int width, final int height);

    }

}
