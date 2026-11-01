package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JigsawBlockEditScreen.class)
public abstract class MixinJigsawBlockEditScreen {

    @Shadow
    private EditBox selectionPriorityEdit;

    @Shadow
    private EditBox placementPriorityEdit;

    @Inject(method = "init", at = @At("RETURN"))
    private void disableWidgets(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            selectionPriorityEdit.active = false;
            placementPriorityEdit.active = false;
        }
    }

}
