package com.viaversion.viafabricplus.injection.mixin.features.v1_7_6.screen;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameModeSwitcherScreen.class)
public abstract class MixinGameModeSwitcherScreen {

    @Mutable
    @Shadow
    @Final
    private static int ALL_SLOTS_WIDTH;

    @Unique
    private GameModeSwitcherScreen.GameModeIcon[] viaFabricPlus$unwrappedGameModes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fixUIWidth(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            final List<GameModeSwitcherScreen.GameModeIcon> selections = new ArrayList<>(Arrays.stream(GameModeSwitcherScreen.GameModeIcon.values()).toList());

            selections.remove(GameModeSwitcherScreen.GameModeIcon.SPECTATOR);
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_2_4tor1_2_5)) {
                selections.remove(GameModeSwitcherScreen.GameModeIcon.ADVENTURE);
            }

            viaFabricPlus$unwrappedGameModes = selections.toArray(GameModeSwitcherScreen.GameModeIcon[]::new);
            ALL_SLOTS_WIDTH = viaFabricPlus$unwrappedGameModes.length * 31 - 5;
        }
    }

    @Redirect(method = "init", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;VALUES:[Lnet/minecraft/client/gui/screens/debug/GameModeSwitcherScreen$GameModeIcon;", opcode = Opcodes.GETSTATIC))
    private GameModeSwitcherScreen.GameModeIcon[] removeNewerGameModes() {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
            return viaFabricPlus$unwrappedGameModes;
        } else {
            return GameModeSwitcherScreen.GameModeIcon.values();
        }
    }

}
