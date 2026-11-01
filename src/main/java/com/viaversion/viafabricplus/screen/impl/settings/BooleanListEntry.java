package com.viaversion.viafabricplus.screen.impl.settings;

import com.viaversion.viafabricplus.api.settings.base.BooleanSetting;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class BooleanListEntry extends VFPListEntry {

    private static final int ACTIVE_COLOR = 0xFF00FF00;
    private static final int INACTIVE_COLOR = 0xFFFF0000;
    private static final int STATE_MARGIN = 2;

    private final BooleanSetting value;

    public BooleanListEntry(BooleanSetting value) {
        this.value = value;
    }

    @Override
    public @NonNull Component getNarration() {
        return this.value.name();
    }

    @Override
    public void mappedMouseClicked() {
        this.value.setActive(!this.value.isActive());
    }

    @Override
    public void mappedRender(GuiGraphicsExtractor context, int entryWidth, int entryHeight) {
        final Font textRenderer = Minecraft.getInstance().font;

        final Component text = this.value.isActive() ? Component.translatable("base.viafabricplus.on") : Component.translatable("base.viafabricplus.off");

        final int offset = textRenderer.width(text) + STATE_MARGIN;
        renderScrollableText(context, this.value.name().copy().withStyle(ChatFormatting.GRAY), offset);
        context.text(textRenderer, text, entryWidth - offset, entryHeight / 2 - textRenderer.lineHeight / 2, this.value.isActive() ? ACTIVE_COLOR : INACTIVE_COLOR);
    }

}
