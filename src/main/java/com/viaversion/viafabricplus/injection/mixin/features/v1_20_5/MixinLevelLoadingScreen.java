package com.viaversion.viafabricplus.injection.mixin.features.v1_20_5;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen {

    @Shadow
    private LevelLoadingScreen.Reason reason;

    @Redirect(method = "extractBackground", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;reason:Lnet/minecraft/client/gui/screens/LevelLoadingScreen$Reason;", opcode = Opcodes.GETFIELD))
    private LevelLoadingScreen.Reason hideDownloadTerrainScreenTransitionEffects(LevelLoadingScreen levelLoadingScreen) {
        if (ViaFabricPlusImpl.impl().visuals().hideDownloadTerrainScreenTransitionEffects().isActive()) {
            return LevelLoadingScreen.Reason.OTHER;
        } else {
            return this.reason;
        }
    }

}
