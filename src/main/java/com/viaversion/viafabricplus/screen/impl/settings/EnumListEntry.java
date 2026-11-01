package com.viaversion.viafabricplus.screen.impl.settings;

import com.viaversion.viafabricplus.api.settings.base.EnumSetting;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import java.util.Arrays;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class EnumListEntry<T extends EnumSetting.EnumValue> extends VFPListEntry {

    private static final int VALUE_MARGIN = 2;

    private final EnumSetting<T> value;

    public EnumListEntry(EnumSetting<T> value) {
        this.value = value;
    }

    @Override
    public @NonNull Component getNarration() {
        return this.value.name();
    }

    @Override
    public void mappedMouseClicked() {
        final T[] values = this.value.values();
        final int currentIndex = Arrays.asList(values).indexOf(this.value.value());
        this.value.setValue(currentIndex + 1 == values.length ? values[0] : values[currentIndex + 1]);
    }

    @Override
    public void mappedRender(GuiGraphicsExtractor context, int entryWidth, int entryHeight) {
        final Font textRenderer = Minecraft.getInstance().font;

        final Component name = this.value.value().component();
        final int offset = textRenderer.width(name) + VALUE_MARGIN;
        renderScrollableText(context, this.value.name().copy().withStyle(ChatFormatting.GRAY), offset);
        context.text(textRenderer, name, entryWidth - offset, entryHeight / 2 - textRenderer.lineHeight / 2, -1);
    }

}
