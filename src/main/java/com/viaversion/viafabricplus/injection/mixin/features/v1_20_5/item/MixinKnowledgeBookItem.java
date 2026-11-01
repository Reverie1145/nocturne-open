package com.viaversion.viafabricplus.injection.mixin.features.v1_20_5.item;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.KnowledgeBookItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KnowledgeBookItem.class)
public abstract class MixinKnowledgeBookItem {

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void removeFullStack(ItemStack instance, int amount, LivingEntity owner, @Local(argsOnly = true) InteractionHand hand) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            if (!owner.hasInfiniteMaterials()) {
                owner.setItemInHand(hand, ItemStack.EMPTY);
            }
        } else {
            instance.consume(amount, owner);
        }
    }

}
