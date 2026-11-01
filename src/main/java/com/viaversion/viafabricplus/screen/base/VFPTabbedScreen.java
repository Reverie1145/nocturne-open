package com.viaversion.viafabricplus.screen.base;

import cn.reverie.render.core.ResolutionHelper;
import com.google.common.collect.ImmutableList;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public abstract class VFPTabbedScreen<T> extends VFPScreen {

    private static final int TAB_BAR_HEIGHT = 24;

    private final Map<T, Double> scrollAmounts = new HashMap<>();
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget, this::onTabSelected, _ -> {
    });

    private VFPTabBar tabBar;
    private EditBox searchBox;
    private ListTab searchTab;
    private List<T> tabKeys;
    private ImmutableList<Tab> tabWidgets;
    private final Map<Tab, VFPList> nvgTabLists = new HashMap<>();
    private Tab currentTab;
    private @Nullable T lastTab;
    private int layoutWidth;
    private int layoutHeight;

    protected VFPTabbedScreen(final Component title, final boolean backButton) {
        super(title, backButton);
    }

    protected boolean useNvgRendering() {
        return false;
    }

    public final int nvgCurrentTabIndex() {
        return this.tabWidgets.indexOf(this.tabManager.getCurrentTab());
    }

    public final void nvgSelectTab(final int index) {
        if (index >= 0 && index < this.tabWidgets.size()) {
            this.tabBar.selectTab(index, true);
        }
    }

    public final @Nullable VFPList nvgCurrentList() {
        final Tab current = this.tabManager.getCurrentTab();
        if (current == null) {
            return null;
        }
        return current == this.searchTab ? this.searchTab.list : this.nvgTabLists.get(current);
    }

    public final @Nullable EditBox nvgSearchBox() {
        return this.searchBox;
    }

    protected abstract List<T> tabs();

    protected abstract Component tabTitle(final T tab);

    protected abstract int entryHeight();

    protected abstract int rowWidth(final int screenWidth);

    protected abstract List<VFPListEntry> entries(final T tab);

    protected abstract List<VFPListEntry> results(final String query);

    protected T initialTab() {
        final List<T> tabs = this.tabs();
        return this.lastTab != null && tabs.contains(this.lastTab) ? this.lastTab : tabs.getFirst();
    }

    protected int listBottomMargin() {
        return FOOTER_HEIGHT;
    }

    @Override
    protected void init() {
        this.layoutWidth = this.useNvgRendering() ? Math.round(ResolutionHelper.getCanvasWidth()) : this.width;
        this.layoutHeight = this.useNvgRendering() ? Math.round(ResolutionHelper.getCanvasHeight()) : this.height;
        this.tabKeys = this.tabs();
        this.nvgTabLists.clear();
        final ImmutableList.Builder<Tab> listTabs = ImmutableList.builder();
        final ImmutableList.Builder<TabButton> tabButtons = ImmutableList.builder();
        for (final T tab : this.tabKeys) {
            final ListTab listTab = new ListTab(tab);
            listTabs.add(listTab);
            tabButtons.add(new MenuTabBar.MenuTabButton(this.tabManager, listTab, 0, TAB_BAR_HEIGHT));
            this.nvgTabLists.put(listTab, listTab.list);
        }
        this.tabWidgets = listTabs.build();
        this.tabBar = new VFPTabBar(CONTENT_TOP, this.layoutWidth, TAB_BAR_HEIGHT, this.tabManager, tabButtons.build(), this.tabWidgets);
        if (!this.useNvgRendering()) {
            this.addRenderableWidget(this.tabBar);
        }

        this.searchTab = new ListTab(null);

        this.searchBox = this.addSearchBar(this::search);

        final int initialTab = Math.max(0, this.tabKeys.indexOf(this.initialTab()));
        this.currentTab = this.tabWidgets.get(initialTab);
        this.tabBar.selectTab(initialTab, false);

        this.tabBar.arrangeElements(this.layoutWidth);
        final int listTop = this.tabBar.getRectangle().bottom();
        this.tabManager.setTabArea(new ScreenRectangle(0, listTop, this.layoutWidth, this.layoutHeight - this.listBottomMargin() - listTop));

        super.init();
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        return this.tabBar.keyPressed(event) || super.keyPressed(event);
    }

    private void search(final String query) {
        if (query.isEmpty()) {
            this.tabManager.setCurrentTab(this.currentTab, false);
            return;
        }

        this.searchTab.list.showResults(query);
        this.tabManager.setCurrentTab(this.searchTab, false);
    }

    private void onTabSelected(final Tab tab) {
        if (tab == this.searchTab) {
            return;
        }

        this.currentTab = tab;

        final int index = this.tabWidgets.indexOf(tab);
        if (index != -1) {
            this.lastTab = this.tabKeys.get(index);
        }

        this.searchBox.setValue("");
    }

    private final class ListTab implements Tab {

        private final @Nullable T tab;
        private final FrameLayout layout = new FrameLayout();
        private final TabList list;

        private ListTab(final @Nullable T tab) {
            this.tab = tab;
            this.list = new TabList(minecraft, layoutWidth, layoutHeight, listBottomMargin(), entryHeight(), tab);
        }

        @Override
        public @NonNull Component getTabTitle() {
            return this.tab == null ? SEARCH_TITLE : tabTitle(this.tab);
        }

        @Override
        public @NonNull Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(final Consumer<AbstractWidget> childrenConsumer) {
            childrenConsumer.accept(this.list);
        }

        @Override
        public void doLayout(final ScreenRectangle screenRectangle) {
            this.list.updateSizeAndPosition(screenRectangle.width(), screenRectangle.height(), screenRectangle.left(), screenRectangle.top());
        }

        @Override
        public @NonNull Layout getLayout() {
            return this.layout;
        }

    }

    private final class TabList extends VFPList {

        private final @Nullable T tab;

        private TabList(final Minecraft minecraftClient, final int width, final int height, final int bottom, final int entryHeight, final @Nullable T tab) {
            super(minecraftClient, width, height, CONTENT_TOP + TAB_BAR_HEIGHT, bottom, entryHeight);
            this.tab = tab;

            if (tab == null) {
                return;
            }

            entries(tab).forEach(this::addEntry);

            setScrollAmount(scrollAmounts.getOrDefault(tab, 0D));
        }

        private void showResults(final String query) {
            this.replaceEntries(results(query));
            this.setScrollAmount(0D);
        }

        @Override
        public int getRowWidth() {
            return rowWidth(this.width);
        }

        @Override
        protected void updateSlotAmount(final double amount) {
            if (this.tab != null) {
                scrollAmounts.put(this.tab, amount);
            }
        }

    }

}
