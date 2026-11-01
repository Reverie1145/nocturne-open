package com.viaversion.viafabricplus.screen.impl;

import com.viaversion.viafabricplus.screen.impl.protocol.AbstractProtocolSelectionScreen;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PerServerVersionScreen extends AbstractProtocolSelectionScreen {

    private final Consumer<ProtocolVersion> selectionConsumer;
    private final Supplier<ProtocolVersion> selectionSupplier;

    public PerServerVersionScreen(final Screen prevScreen, final Consumer<ProtocolVersion> selectionConsumer, final Supplier<ProtocolVersion> selectionSupplier) {
        super(Component.translatable("screen.viafabricplus.force_version"), true);

        this.prevScreen = prevScreen;
        this.selectionConsumer = selectionConsumer;
        this.selectionSupplier = selectionSupplier;
    }

    @Override
    protected void init() {
        super.init();

        this.addFooter(Button.builder(Component.translatable("base.viafabricplus.reset"), _ -> this.selectionConsumer.accept(null)).build());
    }

    @Override
    protected void select(final ProtocolVersion version) {
        this.selectionConsumer.accept(version);
    }

    @Override
    protected boolean selected(final ProtocolVersion version) {
        return version.equals(this.selectionSupplier.get());
    }

    @Override
    protected boolean selectable() {
        return true;
    }

}
