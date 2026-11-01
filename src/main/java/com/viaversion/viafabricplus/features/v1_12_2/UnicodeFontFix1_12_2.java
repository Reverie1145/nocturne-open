package com.viaversion.viafabricplus.features.v1_12_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;

public final class UnicodeFontFix1_12_2 {

    private static boolean enabled = false;
    private static Runnable task = null;

    public static void init() {
        ViaFabricPlus.api().protocolTranslation().addChangeProtocolVersionListener((_, newVersion) -> {
            updateUnicodeFontOverride(newVersion);
        });

        ClientTickEvents.START_CLIENT_TICK.register(_ -> {
            if (task != null) {
                task.run();
                task = null;
            }
        });
    }

    public static void updateUnicodeFontOverride(final ProtocolVersion version) {
        final OptionInstance<Boolean> option = Minecraft.getInstance().options.forceUnicodeFont();

        if (ViaFabricPlusImpl.impl().visuals().forceUnicodeFontForNonAsciiLanguages().isActive(version)) {
            if (Language.getInstance() instanceof ClientLanguage storage) {
                enabled = LanguageUtil.isUnicodeFont1_12_2(storage.storage);
                task = () -> option.set(enabled);
            }
        } else if (enabled) {
            enabled = false;
            task = () -> option.set(false);
        }
    }

}
