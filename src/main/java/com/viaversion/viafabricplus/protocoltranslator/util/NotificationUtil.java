package com.viaversion.viafabricplus.protocoltranslator.util;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public final class NotificationUtil {

    public static void warnIncompatibilityPacket(final String version, final String packet, final @Nullable String methodName) {
        final Logger logger = ViaFabricPlusImpl.impl().logger();
        logger.error("===========================================");
        logger.error("The {} packet (>= {}) could not be remapped without breaking content!", packet, version);
        logger.error("Try disabling mods one by one or using a binary search method to identify the problematic mod.");
        if (methodName != null) {
            logger.error("Mods authors should use {} instead of sending packets directly.", methodName);
        } else {
            logger.error("Mod authors should not send this packet directly.");
        }
        logger.error("Need help? Join our Discord: https://discord.gg/viaversion");
        logger.error("===========================================");
    }

}
