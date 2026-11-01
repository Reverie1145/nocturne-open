package com.viaversion.viafabricplus.protocoltranslator.impl.viaversion;

import com.viaversion.viaversion.configuration.AbstractViaConfig;
import com.viaversion.viaversion.util.ConfigSection;
import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public final class ViaFabricPlusConfig extends AbstractViaConfig {

    public ViaFabricPlusConfig(File configFile, Logger logger) {
        super(configFile, logger);
    }

    @Override
    protected boolean updateConfig() {
        boolean modified = false;

        final ConfigSection existingConfig = originalRootSection();
        if (existingConfig == null) {

            set("fix-infested-block-breaking", false);
            set("shield-blocking", false);
            set("no-delay-shield-blocking", true);
            set("handle-invalid-item-count", true);
            set("chunk-border-fix", true);
            modified = true;
        }

        if (modified || !existingConfig.contains("send-player-details")) {
            set("send-player-details", false);
            modified = true;
        }
        return super.updateConfig() || modified;
    }

    @Override
    public List<String> getUnsupportedOptions() {
        final List<String> unsupported = super.getUnsupportedOptions();
        unsupported.add("simulate-pt");
        unsupported.add("fix-1_21-placement-rotation");
        unsupported.add("team-colour-fix");
        unsupported.add("cancel-swing-in-inventory");
        unsupported.add("use-1_8-hitbox-margin");
        return unsupported;
    }

    @Override
    public boolean isCheckForUpdates() {
        return false;
    }

    @Override
    public boolean isSimulatePlayerTick() {
        return false;
    }

    @Override
    public boolean fix1_21PlacementRotation() {
        return false;
    }

    @Override
    public boolean is1_13TeamColourFix() {
        return false;
    }

    @Override
    public boolean cancelSwingInInventory() {
        return false;
    }

    @Override
    public boolean cancelBlockSounds() {
        return false;
    }

    @Override
    public boolean use1_8HitboxMargin() {
        return false;
    }

}
