package cn.reverie.mixin.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int nocturneGetLeftPos();

    @Accessor("topPos")
    int nocturneGetTopPos();

    @Accessor("imageWidth")
    int nocturneGetImageWidth();

    @Accessor("imageHeight")
    int nocturneGetImageHeight();

    @Accessor("hoveredSlot")
    Slot nocturneGetHoveredSlot();

    @Accessor("hoveredSlot")
    void nocturneSetHoveredSlot(Slot slot);

    @Invoker("getHoveredSlot")
    Slot nocturneInvokeGetHoveredSlot(double x, double y);

    @Invoker("slotClicked")
    void nocturneInvokeSlotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput);
}
