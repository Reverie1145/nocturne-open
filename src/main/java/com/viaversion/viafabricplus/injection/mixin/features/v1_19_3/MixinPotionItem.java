package com.viaversion.viafabricplus.injection.mixin.features.v1_19_3;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PotionItem.class)
public abstract class MixinPotionItem extends Item {

    public MixinPotionItem(final Properties settings) {
        super(settings);
    }

    @Override
    public boolean isFoil(final ItemStack stack) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            return stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).hasEffects();
        } else {
            return super.isFoil(stack);
        }
    }

}
