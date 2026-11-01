package com.viaversion.viafabricplus.injection.mixin.features.v1_14_4;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CraftingMenu.class)
public abstract class MixinCraftingMenu extends AbstractCraftingMenu {

    public MixinCraftingMenu(MenuType<?> type, int syncId, int width, int height) {
        super(type, syncId, width, height);
    }

    @Redirect(method = "quickMoveStack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/CraftingMenu;moveItemStackTo(Lnet/minecraft/world/item/ItemStack;IIZ)Z", ordinal = 1))
    private boolean noShiftClickMoveIntoCraftingTable(CraftingMenu instance, ItemStack itemStack, int startIndex, int endIndex, boolean fromLast) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_14_4) && this.moveItemStackTo(itemStack, startIndex, endIndex, fromLast);
    }

}
