package com.viaversion.viafabricplus.injection.mixin.features.v1_6_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @Redirect(method = {"mouseClicked", "mouseReleased"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;hasShiftDown()Z"))
    private boolean disableShiftClickItems(final MouseButtonEvent instance) {
        return instance.hasShiftDown() && ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_6_1);
    }

}
