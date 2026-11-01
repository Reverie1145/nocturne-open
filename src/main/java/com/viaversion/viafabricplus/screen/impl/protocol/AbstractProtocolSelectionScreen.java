package com.viaversion.viafabricplus.screen.impl.protocol;

import cn.reverie.gui.vfp.VfpVersionLayout;
import cn.reverie.gui.vfp.VfpVersionScreenRenderer;
import cn.reverie.render.core.ResolutionHelper;
import cn.reverie.render.ui.NvgScreen;
import com.viaversion.viafabricplus.protocoltranslator.util.ProtocolVersionDetector;
import com.viaversion.viafabricplus.screen.base.VFPTabbedScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public abstract class AbstractProtocolSelectionScreen extends VFPTabbedScreen<ProtocolCategory> implements NvgScreen {

    private static final int SLOT_HEIGHT = 32;
    private static final int ROW_WIDTH = 260;

    private VfpVersionScreenRenderer nvgRenderer;

    protected AbstractProtocolSelectionScreen(final Component title, final boolean backButton) {
        super(title, backButton);
    }

    protected abstract void select(final ProtocolVersion version);

    protected abstract boolean selected(final ProtocolVersion version);

    protected abstract boolean selectable();

    public final boolean nvgIsSelectable() {
        return this.selectable();
    }

    public final boolean nvgIsSelected(final ProtocolVersion version) {
        return this.selected(version);
    }

    @Override
    protected boolean useNvgRendering() {
        return true;
    }

    public final List<String> nvgTabTitles() {
        final List<String> titles = new ArrayList<>();
        for (final ProtocolCategory tab : this.tabs()) {
            titles.add(tabTitle(tab).getString());
        }
        return titles;
    }

    @Override
    public void renderNvg(final float mouseX, final float mouseY, final float partialTicks) {
        if (this.nvgRenderer == null) {
            this.nvgRenderer = new VfpVersionScreenRenderer(this);
        }
        this.nvgRenderer.render(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void init() {
        super.init();

        if (this.nvgRenderer == null) {
            this.nvgRenderer = new VfpVersionScreenRenderer(this);
        }
        this.nvgRenderer.resetForOpen();

        if (this.useNvgRendering()) {

            final int[] search = VfpVersionLayout.searchRect((int) ResolutionHelper.getCanvasWidth());
            final EditBox box = this.nvgSearchBox();
            if (box != null) {
                box.setX(search[0]);
                box.setY(search[1]);
                box.setWidth(search[2]);
                box.setHeight(search[3]);
            }
        }
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubled) {
        final double mouseX = ResolutionHelper.toCanvasX(event.x());
        final double mouseY = ResolutionHelper.toCanvasY(event.y());
        final MouseButtonEvent canvasEvent = new MouseButtonEvent(mouseX, mouseY, event.buttonInfo());
        if (this.nvgRenderer != null && this.nvgRenderer.onMousePressed((float) mouseX, (float) mouseY)) {
            return true;
        }
        final EditBox searchBox = this.nvgSearchBox();
        if (searchBox != null && searchBox.mouseClicked(canvasEvent, doubled)) {
            return true;
        }
        return super.mouseClicked(canvasEvent, doubled);
    }

    @Override
    public boolean mouseReleased(final @NonNull MouseButtonEvent event) {
        if (this.nvgRenderer != null) {
            this.nvgRenderer.onMouseReleased();
        }
        return super.mouseReleased(new MouseButtonEvent(
            ResolutionHelper.toCanvasX(event.x()), ResolutionHelper.toCanvasY(event.y()), event.buttonInfo()
        ));
    }

    @Override
    public boolean mouseDragged(final @NonNull MouseButtonEvent event, final double dragX, final double dragY) {
        final double mouseX = ResolutionHelper.toCanvasX(event.x());
        final double mouseY = ResolutionHelper.toCanvasY(event.y());
        final double canvasDragX = ResolutionHelper.toCanvasDelta(dragX);
        final double canvasDragY = ResolutionHelper.toCanvasDelta(dragY);
        if (this.nvgRenderer != null && this.nvgRenderer.onMouseDragged((float) mouseX, (float) mouseY)) {
            return true;
        }
        return super.mouseDragged(
            new MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), canvasDragX, canvasDragY
        );
    }

    @Override
    public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
        return super.mouseScrolled(
            ResolutionHelper.toCanvasX(x), ResolutionHelper.toCanvasY(y), scrollX, scrollY
        );
    }

    @Override
    protected List<ProtocolCategory> tabs() {
        return List.of(ProtocolCategory.values());
    }

    @Override
    protected Component tabTitle(final ProtocolCategory tab) {
        return tab.title();
    }

    @Override
    protected int entryHeight() {
        return SLOT_HEIGHT;
    }

    @Override
    protected int rowWidth(final int screenWidth) {
        return Math.min(ROW_WIDTH, screenWidth - 20);
    }

    @Override
    protected List<VFPListEntry> entries(final ProtocolCategory tab) {
        final List<VFPListEntry> entries = new ArrayList<>();
        if (tab == ProtocolCategory.MODERN) {

            entries.add(new ProtocolSlot(ProtocolVersionDetector.AUTO_DETECT_VERSION, this));
        }

        for (final ProtocolVersion version : ProtocolVersion.getReversedProtocols()) {
            if (version != ProtocolVersionDetector.AUTO_DETECT_VERSION && ProtocolCategory.of(version) == tab) {
                entries.add(new ProtocolSlot(version, this));
            }
        }
        return entries;
    }

    @Override
    protected List<VFPListEntry> results(final String query) {
        return ProtocolVersion.getReversedProtocols().stream()
            .filter(version -> matches(version, query))
            .<VFPListEntry>map(version -> new ProtocolSlot(version, this))
            .toList();
    }

    @Override
    protected ProtocolCategory initialTab() {

        for (final ProtocolVersion version : ProtocolVersion.getReversedProtocols()) {
            if (this.selected(version)) {

                return version == ProtocolVersionDetector.AUTO_DETECT_VERSION ? ProtocolCategory.MODERN : ProtocolCategory.of(version);
            }
        }

        return ProtocolCategory.MODERN;
    }

    private static boolean matches(final ProtocolVersion version, final String query) {
        if (version.getName().toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }

        for (final String includedVersion : version.getIncludedVersions()) {
            if (includedVersion.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }

        final ProtocolVersionMetadata metadata = ProtocolVersionMetadata.of(version);
        return metadata != null && metadata.title() != null && metadata.title().toLowerCase(Locale.ROOT).contains(query);
    }

}
