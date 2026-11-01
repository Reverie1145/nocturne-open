package com.viaversion.viafabricplus.protocoltranslator.impl.command.classic;

import com.viaversion.viafabricplus.injection.access.core.IExtensionProtocolMetadataStorage;
import com.viaversion.viafabricplus.protocoltranslator.impl.command.VFPSubCommand;
import com.viaversion.viaversion.api.command.ViaCommandSender;
import net.minecraft.ChatFormatting;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.storage.ExtensionProtocolMetadataStorage;

public final class ListExtensionsCommand implements VFPSubCommand {

    @Override
    public String name() {
        return "listextensions";
    }

    @Override
    public String description() {
        return "Shows all classic extensions (only for " + LegacyProtocolVersion.c0_30cpe.getName() + ")";
    }

    @Override
    public boolean execute(ViaCommandSender sender, String[] args) {
        if (getUser() == null || !getUser().has(ExtensionProtocolMetadataStorage.class)) {
            sendMessage(sender, ChatFormatting.RED + "Only for " + LegacyProtocolVersion.c0_30cpe.getName());
            return true;
        }
        ((IExtensionProtocolMetadataStorage) getUser().get(ExtensionProtocolMetadataStorage.class)).viaFabricPlus$getServerExtensions().forEach((extension, version) -> {
            sendMessage(sender, ChatFormatting.GREEN + extension.getName() + ChatFormatting.GOLD + " v" + version);
        });
        return true;
    }

}
