package com.viaversion.viafabricplus.injection.mixin.features.v1_4_2;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinAbstractContainerScreen {

    @WrapWithCondition(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", ordinal = 0))
    private boolean disableItemCloning(AbstractContainerScreen<?> instance, Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
        return ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_4_2);
    }

    @Redirect(method = "checkHotbarKeyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;matches(Lnet/minecraft/client/input/KeyEvent;)Z", ordinal = 1))
    private boolean disableHotbarKeys(final KeyMapping instance, final KeyEvent event) {
        return instance.matches(event) && ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(LegacyProtocolVersion.r1_4_2);
    }

}
