package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2.screen;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_13_2.MouseSensitivity1_13_2;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MouseSettingsScreen.class)
public abstract class MixinMouseSettingsScreen extends OptionsSubScreen {

    public MixinMouseSettingsScreen(Screen parent, Options gameOptions, Component title) {
        super(parent, gameOptions, title);
    }

    @Override
    public void extractRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2) && this.list.findOption(this.options.sensitivity()).isHovered()) {
            graphics.setTooltipForNextFrame(font, Component.nullToEmpty("<=1.13.2 Sensitivity: " + MouseSensitivity1_13_2.get1_13SliderValue(this.options.sensitivity().get().floatValue()).valueInt() + "%"), mouseX, mouseY);
        }
    }

}
