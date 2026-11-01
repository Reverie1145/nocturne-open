package com.viaversion.viafabricplus.screen.base.list;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.NonNull;

public final class VFPTextEntry extends VFPListEntry {

    private final Component text;

    public VFPTextEntry(final Component text) {
        this.text = text;
    }

    @Override
    public @NonNull Component getNarration() {
        return this.text;
    }

    @Override
    public void extractContent(final @NonNull GuiGraphicsExtractor context, final int mouseX, final int mouseY, final boolean hovered, final float deltaTicks) {
        final Matrix3x2fStack matrices = context.pose();

        matrices.pushMatrix();
        matrices.translate(getContentX(), getContentY());
        mappedRender(context, getContentWidth(), getContentHeight());
        matrices.popMatrix();
    }

    @Override
    public void mappedRender(final GuiGraphicsExtractor context, final int entryWidth, final int entryHeight) {
        final Font font = Minecraft.getInstance().font;

        context.centeredText(font, this.text, entryWidth / 2, entryHeight / 2 - font.lineHeight / 2, -1);
    }

}
