package com.viaversion.viafabricplus.injection.mixin.features.v1_5;

import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen extends Screen {

    protected MixinAbstractContainerScreen(final Component component) {
        super(component);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void disableItemDragging(final MouseButtonEvent event, final double dx, final double dy, final CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThan(LegacyProtocolVersion.r1_5tor1_5_1)) {
            cir.setReturnValue(super.mouseDragged(event, dx, dy));
        }
    }

}
