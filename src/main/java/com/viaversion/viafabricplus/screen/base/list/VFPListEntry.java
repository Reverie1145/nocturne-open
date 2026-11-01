package com.viaversion.viafabricplus.screen.base.list;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.NonNull;

public abstract class VFPListEntry extends ObjectSelectionList.Entry<VFPListEntry> {

    private static final int SCISSORS_OFFSET = 4;
    public static final int SLOT_MARGIN = 3;

    public void mappedRender(GuiGraphicsExtractor context, int entryWidth, int entryHeight) {

    }

    public void mappedMouseClicked() {

    }

    @Override
    public boolean mouseClicked(final MouseButtonEvent click, final boolean doubled) {
        mappedMouseClicked();
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
        return super.mouseClicked(click, doubled);
    }

    public void renderScrollableText(final GuiGraphicsExtractor context, final Component text, final int offset) {
        final Font font = Minecraft.getInstance().font;

        final int textY = getContentHeight() / 2 - font.lineHeight / 2;
        final int fontWidth = font.width(text);
        if (fontWidth > (getContentWidth() - offset)) {
            final double time = (double) Util.getMillis() / 1000.0;
            final double interpolateEnd = fontWidth - (getContentWidth() - offset - (SCISSORS_OFFSET + SLOT_MARGIN));

            final double interpolatedValue = Math.sin((Math.PI / 2) * Math.cos(Math.PI * 2 * time / Math.max(interpolateEnd * 0.5, 3.0))) / 2.0 + 0.5;

            context.enableScissor(0, 0, getContentWidth() - offset - SCISSORS_OFFSET, getContentHeight());
            context.text(font, text, SLOT_MARGIN - (int) Mth.lerp(interpolatedValue, 0.0, interpolateEnd), textY, -1);
            context.disableScissor();
        } else {
            context.text(font, text, SLOT_MARGIN, textY, -1);
        }
    }

    @Override
    public void extractContent(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final boolean hovered, final float deltaTicks) {
        final Matrix3x2fStack matrices = graphics.pose();

        matrices.pushMatrix();
        matrices.translate(getContentX(), getContentY());
        graphics.fill(0, 0, getContentWidth(), getContentHeight(), Integer.MIN_VALUE);
        mappedRender(graphics, getContentWidth(), getContentHeight());
        matrices.popMatrix();
    }

}
