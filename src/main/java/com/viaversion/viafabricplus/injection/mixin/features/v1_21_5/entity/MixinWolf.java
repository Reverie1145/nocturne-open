package com.viaversion.viafabricplus.injection.mixin.features.v1_21_5.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_21_5.WolfHealthTracker1_14_4;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Wolf.class)
public abstract class MixinWolf extends TamableAnimal implements NeutralMob {

    @Shadow
    public abstract DyeColor getCollarColor();

    @Shadow
    protected abstract void setCollarColor(DyeColor color);

    protected MixinWolf(EntityType<? extends TamableAnimal> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void fixWolfInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        final ItemStack itemStack = player.getItemInHand(hand);
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            final Item item = itemStack.getItem();
            if (this.isTame()) {
                final FoodProperties foodComponent = itemStack.get(DataComponents.FOOD);
                if (foodComponent != null) {
                    if (this.isFood(itemStack) && WolfHealthTracker1_14_4.getWolfHealth(this) < 20.0F) {
                        if (!player.getAbilities().instabuild) itemStack.shrink(1);
                        this.heal(foodComponent.nutrition());
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        return;
                    }
                } else if (item instanceof DyeItem) {
                    final DyeColor dyeColor = itemStack.get(DataComponents.DYE);
                    if (dyeColor != null && dyeColor != this.getCollarColor()) {
                        this.setCollarColor(dyeColor);
                        if (!player.getAbilities().instabuild) itemStack.shrink(1);
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        return;
                    }
                }
            } else if (item == Items.BONE && !this.isAngry()) {
                if (!player.getAbilities().instabuild) itemStack.shrink(1);
                cir.setReturnValue(InteractionResult.SUCCESS);
                return;
            }

            cir.setReturnValue(super.mobInteract(player, hand));
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5) && this.isTame()) {

            if (itemStack.is(Items.SHEARS)
                && this.isOwnedBy(player)
                && this.isWearingBodyArmor()
                && (!EnchantmentHelper.has(this.getBodyArmorItem(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE) || player.isCreative())) {
                this.playSound(SoundEvents.ARMOR_UNEQUIP_WOLF);
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }

}
