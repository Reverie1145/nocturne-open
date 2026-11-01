package com.viaversion.viafabricplus.screen.impl.protocol;

import com.viaversion.viafabricplus.protocoltranslator.util.ProtocolVersionDetector;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public final class ProtocolSlot extends VFPListEntry {

    private static final Identifier AUTO_DETECT_ICON = Identifier.withDefaultNamespace("textures/item/spyglass.png");

    private static final int ICON_SIZE = 16;
    private static final int TEXT_OFFSET = SLOT_MARGIN + ICON_SIZE + SLOT_MARGIN;

    private static final int SELECTED_COLOR = VFPScreen.ACCENT_COLOR;
    private static final int SELECTED_BACKGROUND_COLOR = 0x4058A6FF;
    private static final int SUBTITLE_COLOR = 0xFFAAAAAA;
    private static final int DISABLED_COLOR = 0xFF808080;

    private final ProtocolVersion protocolVersion;
    private final AbstractProtocolSelectionScreen screen;
    private final @Nullable ProtocolVersionMetadata metadata;
    private final Identifier icon;

    public ProtocolSlot(final ProtocolVersion protocolVersion, final AbstractProtocolSelectionScreen screen) {
        this.protocolVersion = protocolVersion;
        this.screen = screen;
        this.metadata = ProtocolVersionMetadata.of(protocolVersion);
        this.icon = resolveIcon(protocolVersion);
    }

    private static Identifier resolveIcon(final ProtocolVersion protocolVersion) {

        if (protocolVersion == ProtocolVersionDetector.AUTO_DETECT_VERSION) {
            return AUTO_DETECT_ICON;
        }

        return ProtocolVersionMetadata.icon(protocolVersion);
    }

    public ProtocolVersion protocolVersion() {
        return this.protocolVersion;
    }

    public @Nullable ProtocolVersionMetadata metadata() {
        return this.metadata;
    }

    public Identifier icon() {
        return this.icon;
    }

    @Override
    public @NonNull Component getNarration() {
        return Component.nullToEmpty(this.protocolVersion.getName());
    }

    @Override
    public void mappedMouseClicked() {
        if (this.screen.selectable()) {
            this.screen.select(this.protocolVersion);
        }
    }

    @Override
    public void mappedRender(final GuiGraphicsExtractor context, final int entryWidth, final int entryHeight) {
        final boolean selectable = this.screen.selectable();
        final boolean selected = this.screen.selected(this.protocolVersion);
        if (selected && selectable) {
            context.fill(0, 0, entryWidth, entryHeight, SELECTED_BACKGROUND_COLOR);
        }

        context.blit(RenderPipelines.GUI_TEXTURED, this.icon, SLOT_MARGIN, (entryHeight - ICON_SIZE) / 2, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        final Font font = Minecraft.getInstance().font;
        final int nameColor = !selectable ? DISABLED_COLOR : selected ? SELECTED_COLOR : -1;
        final int centeredY = entryHeight / 2 - font.lineHeight / 2;
        if (this.metadata == null) {
            context.text(font, this.protocolVersion.getName(), TEXT_OFFSET, centeredY, nameColor);
            return;
        }

        final int subtitleColor = !selectable ? DISABLED_COLOR : SUBTITLE_COLOR;
        final Component releaseDate = this.metadata.formattedReleaseDate();
        final int releaseDateX = entryWidth - font.width(releaseDate) - SLOT_MARGIN;
        context.text(font, releaseDate, releaseDateX, centeredY, subtitleColor);

        if (this.metadata.title() == null) {

            context.text(font, this.protocolVersion.getName(), TEXT_OFFSET, centeredY, nameColor);
            return;
        }

        context.text(font, this.protocolVersion.getName(), TEXT_OFFSET, entryHeight / 2 - font.lineHeight - 1, nameColor);

        context.enableScissor(TEXT_OFFSET, 0, releaseDateX - SLOT_MARGIN, entryHeight);
        context.text(font, this.metadata.title(), TEXT_OFFSET, entryHeight / 2 + 1, subtitleColor);
        context.disableScissor();
    }

}
