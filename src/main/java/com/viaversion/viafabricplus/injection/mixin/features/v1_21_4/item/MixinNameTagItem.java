package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4.item;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NameTagItem.class)
public abstract class MixinNameTagItem {

    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void dontAllowNameTagsOnCreaking(ItemStack itemStack, Player player, LivingEntity target, InteractionHand type, CallbackInfoReturnable<InteractionResult> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4) && target instanceof Creaking) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

}
