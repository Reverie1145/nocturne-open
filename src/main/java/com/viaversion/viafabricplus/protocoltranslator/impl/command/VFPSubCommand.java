package com.viaversion.viafabricplus.protocoltranslator.impl.command;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.util.ChatUtil;
import com.viaversion.viaversion.api.command.ViaCommandSender;
import com.viaversion.viaversion.api.command.ViaSubCommand;
import com.viaversion.viaversion.api.connection.UserConnection;

public interface VFPSubCommand extends ViaSubCommand {

    default void sendMessage(final ViaCommandSender sender, final String message) {
        ViaSubCommand.super.sendMessage(sender, ChatUtil.PREFIX + " " + message);
    }

    default UserConnection getUser() {
        return ViaFabricPlus.api().userConnection();
    }

}
