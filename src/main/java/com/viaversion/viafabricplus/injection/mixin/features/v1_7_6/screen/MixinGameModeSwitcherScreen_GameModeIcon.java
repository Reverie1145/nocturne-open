package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.screen;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(GameModeSwitcherScreen.GameModeIcon.class)
public abstract class MixinGameModeSwitcherScreen_GameModeIcon {

    @Shadow
    @Final
    public static GameModeSwitcherScreen.GameModeIcon SURVIVAL;

    @Shadow
    @Final
    public static GameModeSwitcherScreen.GameModeIcon CREATIVE;

    @Inject(method = "getNext", at = @At("HEAD"), cancellable = true)
    private void unwrapGameModes(CallbackInfoReturnable<GameModeSwitcherScreen.GameModeIcon> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            switch ((GameModeSwitcherScreen.GameModeIcon) (Object) this) {
                case CREATIVE -> cir.setReturnValue(SURVIVAL);
                case SURVIVAL -> {
                    if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
                        cir.setReturnValue(CREATIVE);
                    } else {
                        cir.setReturnValue(GameModeSwitcherScreen.GameModeIcon.ADVENTURE);
                    }
                }
                case ADVENTURE -> cir.setReturnValue(CREATIVE);
            }
        }
    }

}
