package com.viaversion.viafabricplus.protocoltranslator.impl.command;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.api.command.ViaCommandSender;

public final class SettingsCommand implements VFPSubCommand {

    @Override
    public String name() {
        return "settings";
    }

    @Override
    public String description() {
        return "Opens ViaFabricPlus's configuration/options screen";
    }

    @Override
    public boolean execute(ViaCommandSender sender, String[] args) {
        ViaFabricPlusImpl.impl().screens().settingsScreen().open(null);
        return true;
    }

}
