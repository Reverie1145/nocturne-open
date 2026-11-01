package com.viaversion.viafabricplus.injection.mixin.features.v1_18_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureBlockEditScreen.class)
public abstract class MixinStructureBlockEditScreen {

    @Shadow
    private EditBox nameEdit;

    @Inject(method = "init", at = @At("TAIL"))
    private void changeInputNameMaxLength(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_18_2)) {
            this.nameEdit.setMaxLength(64);
        }
    }

}
