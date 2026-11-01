package com.viaversion.viafabricplus.screen.base;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;

public final class VFPTabBar extends MenuTabBar {

    public VFPTabBar(final int y, final int width, final int height, final TabManager tabManager, final ImmutableList<TabButton> tabButtons, final ImmutableList<Tab> tabs) {
        super(0, y, width, height, tabManager, tabButtons, tabs);
    }

    @Override
    public void arrangeElements(final int width) {
        super.arrangeElements(width);
        this.layout.setY(this.getY());
    }

}
