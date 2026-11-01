package com.viaversion.viafabricplus.screen.impl.settings;

import com.viaversion.viafabricplus.api.settings.base.VersionedBooleanSetting;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public final class VersionedBooleanListEntry extends VFPListEntry {

    private static final int ACTIVE_COLOR = 0xFF00FF00;
    private static final int INACTIVE_COLOR = 0xFFFF0000;
    private static final int STATE_MARGIN = 2;

    private final VersionedBooleanSetting value;

    public VersionedBooleanListEntry(VersionedBooleanSetting value) {
        this.value = value;
    }

    @Override
    public @NonNull Component getNarration() {
        return this.value.name();
    }

    @Override
    public void mappedMouseClicked() {
        this.value.setActive(!this.value.value());
    }

    @Override
    public void mappedRender(GuiGraphicsExtractor context, int entryWidth, int entryHeight) {
        final Font textRenderer = Minecraft.getInstance().font;

        final Component text = this.value.value() ? Component.translatable("base.viafabricplus.on") : Component.translatable("base.viafabricplus.off");

        final int offset = textRenderer.width(text) + STATE_MARGIN;
        renderScrollableText(context, Component.nullToEmpty(ChatFormatting.GRAY + this.value.name().getString() + " " + ChatFormatting.RESET + this.value.versionRange().toString()), offset);
        context.text(textRenderer, text, entryWidth - offset, entryHeight / 2 - textRenderer.lineHeight / 2, this.value.value() ? ACTIVE_COLOR : INACTIVE_COLOR);
    }

}
