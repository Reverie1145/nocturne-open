package com.viaversion.viafabricplus.screen.base.list;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import org.jspecify.annotations.NonNull;

public class VFPList extends ObjectSelectionList<VFPListEntry> {

    private static final int ENTRIES_PER_SCROLL_STEP = 5;
    private static final int MAX_SCROLL_STEPS = 2;

    public VFPList(Minecraft minecraftClient, int width, int height, int top, int bottom, int entryHeight) {
        super(minecraftClient, width, height - top - bottom, top, entryHeight);
    }

    @Override
    public void setScrollAmount(double scrollY) {
        super.setScrollAmount(scrollY);
        updateSlotAmount(scrollAmount());
    }

    @Override
    protected double scrollRate() {
        final int steps = Math.min(this.getItemCount() / ENTRIES_PER_SCROLL_STEP, MAX_SCROLL_STEPS);
        return Math.max(super.scrollRate(), steps * (double) this.defaultEntryHeight);
    }

    @Override
    protected void extractSelection(final @NonNull GuiGraphicsExtractor graphics, final @NonNull VFPListEntry entry, final int outlineColor) {

    }

    protected void updateSlotAmount(final double amount) {

    }

}
