package com.viaversion.viafabricplus.injection.mixin.features.v1_20_5.item;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.v1_20_5.IDisplayDefault;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemAttributeModifiers.Display.Default.class)
public abstract class MixinItemAttributeModifiers_Display_Default implements IDisplayDefault {

    @Unique
    private ItemEnchantments viaFabricPlus$itemEnchantments;

    @Override
    public void viaFabricPlus$setItemEnchantments(final ItemEnchantments itemEnchantments) {
        viaFabricPlus$itemEnchantments = itemEnchantments;
    }

    @Redirect(method = "apply", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getAttributeBaseValue(Lnet/minecraft/core/Holder;)D", ordinal = 0))
    private double fixAttackDamageCalculation(Player instance, Holder<Attribute> registryEntry) {
        double value = 0.0;
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            for (Holder<Enchantment> enchantment : viaFabricPlus$itemEnchantments.keySet()) {
                if (enchantment.is(Enchantments.SHARPNESS)) {
                    final int level = viaFabricPlus$itemEnchantments.getLevel(enchantment);
                    if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                        value = level * 1.25F;
                    } else {
                        value = 1.0F + (float) Math.max(0, level - 1) * 0.5F;
                    }
                    break;
                }
            }
        }

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            return value;
        } else {
            return instance.getAttributeBaseValue(registryEntry) + value;
        }
    }

}
