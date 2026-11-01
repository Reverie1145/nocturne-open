package com.viaversion.viafabricplus.injection.mixin.features.c0_28;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class MixinCreativeModeInventoryScreen {

    @Inject(method = "init", at = @At("RETURN"))
    private void replaceCreativeMenu(CallbackInfo ci) {
        if (ViaFabricPlusImpl.impl().visuals().replaceCreativeInventory().isActive()) {
            Minecraft.getInstance().gui.setScreen(ViaFabricPlusImpl.impl().screens().gridItemSelectionScreen());
        }
    }

}
