package com.viaversion.viafabricplus.protocoltranslator.impl.command;

import com.viaversion.viaversion.api.command.ViaCommandSender;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class ViaFabricPlusCommandSender implements ViaCommandSender {

    private final SharedSuggestionProvider source;

    public ViaFabricPlusCommandSender(final SharedSuggestionProvider source) {
        this.source = source;
    }

    @Override
    public boolean hasPermission(String s) {
        return true;
    }

    @Override
    public void sendMessage(String s) {
        ((FabricClientCommandSource) source).sendFeedback(Component.nullToEmpty(s.replace("/viaversion", "/viafabricplus")));
    }

    @Override
    public UUID getUUID() {
        return ((FabricClientCommandSource) source).getPlayer().getUUID();
    }

    @Override
    public String getName() {
        return ((FabricClientCommandSource) source).getPlayer().getName().getString();
    }

}
