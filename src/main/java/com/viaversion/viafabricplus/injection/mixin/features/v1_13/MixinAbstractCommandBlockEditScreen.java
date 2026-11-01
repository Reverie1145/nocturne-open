package com.viaversion.viafabricplus.injection.mixin.features.v1_13;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.inventory.AbstractCommandBlockEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractCommandBlockEditScreen.class)
public abstract class MixinAbstractCommandBlockEditScreen {

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;updateCommandInfo()V"))
    private void cancelAutoComplete(CommandSuggestions instance) {
        if (ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_13)) {
            instance.updateCommandInfo();
        }
    }

}
