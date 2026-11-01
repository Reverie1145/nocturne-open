package com.viaversion.viafabricplus.util;

import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ChatUtil {

    public static final String PREFIX = ChatFormatting.WHITE + "[" + ChatFormatting.BLUE + "ViaFabricPlus" + ChatFormatting.WHITE + "]";
    public static final Component PREFIX_TEXT = Component.literal("[").withStyle(ChatFormatting.WHITE).append(Component.literal("ViaFabricPlus").withColor(VFPScreen.ACCENT_COLOR)).append("]");

    public static Component prefixText(final String message) {
        return prefixText(Component.nullToEmpty(message));
    }

    public static Component prefixText(final Component message) {
        return Component.empty().append(PREFIX_TEXT).append(" ").append(message);
    }

    public static void sendPrefixedMessage(final Component message) {
        if (Minecraft.getInstance().isSameThread()) {
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(prefixText(message));
        } else {
            Minecraft.getInstance().execute(() -> sendPrefixedMessage(message));
        }
    }

}
