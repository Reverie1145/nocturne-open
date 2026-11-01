package com.viaversion.viafabricplus.screen.impl;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.screen.ScreensImpl;
import com.viaversion.viafabricplus.screen.impl.protocol.AbstractProtocolSelectionScreen;
import com.viaversion.viafabricplus.screen.impl.serverlist.BetaCraftServerListScreen;
import com.viaversion.viafabricplus.screen.impl.serverlist.ClassiCubeServerListScreen;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class ViaFabricPlusScreen extends AbstractProtocolSelectionScreen {

    public ViaFabricPlusScreen() {
        super(Component.nullToEmpty("ViaFabricPlus"), true);
    }

    @Override
    protected void init() {
        super.init();

        final ScreensImpl screens = ViaFabricPlusImpl.impl().screens();

        final boolean disconnected = Minecraft.getInstance().getConnection() == null;

        final Button classiCube = Button.builder(ClassiCubeServerListScreen.TITLE, _ -> {
            if (ClassiCubeServerListScreen.loggedIn()) {
                screens.classiCubeServerListScreen().open(this);
            } else {
                screens.classiCubeLoginScreen().open(this);
            }
        }).build();
        classiCube.active = disconnected;

        final Button betaCraft = Button.builder(BetaCraftServerListScreen.TITLE, _ -> screens.betaCraftServerListScreen().open(this)).build();
        betaCraft.active = disconnected;

        this.addFooter(classiCube, betaCraft,
            Button.builder(Component.translatable("base.viafabricplus.settings"), _ -> screens.settingsScreen().open(this)).build(),
            Button.builder(Component.translatable("report.viafabricplus.button"), _ -> screens.reportIssuesScreen().open(this)).build());
    }

    @Override
    protected void select(final ProtocolVersion version) {
        ViaFabricPlus.api().setTargetVersion(version);
    }

    @Override
    protected boolean selected(final ProtocolVersion version) {
        return ViaFabricPlus.api().targetVersion().equals(version);
    }

    @Override
    protected boolean selectable() {

        return Minecraft.getInstance().getConnection() == null;
    }

}
