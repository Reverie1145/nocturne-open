package com.viaversion.viafabricplus.injection.mixin.features.v1_8;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusHandItemProvider;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.Objects;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.commons.lang3.mutable.MutableObject;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void cancelOffHandItemInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8) && !InteractionHand.MAIN_HAND.equals(hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void cancelOffHandBlockPlace(LocalPlayer player, InteractionHand hand, BlockHitResult blockHit, CallbackInfoReturnable<InteractionResult> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8) && !InteractionHand.MAIN_HAND.equals(hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Redirect(method = "lambda$useItem$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult eitherSuccessOrPass(ItemStack instance, Level level, Player player, InteractionHand hand, @Local(name = "itemStack") ItemStack itemStack) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            final int count = instance.getCount();

            final InteractionResult actionResult = instance.use(level, player, hand);
            final ItemStack output;
            if (actionResult instanceof InteractionResult.Success success) {
                output = Objects.requireNonNullElseGet(success.heldItemTransformedTo(), () -> player.getItemInHand(hand));
            } else {
                output = player.getItemInHand(hand);
            }

            final boolean accepted = !output.isEmpty() && (output != itemStack || output.getCount() != count);
            if (actionResult.consumesAction() == accepted) {
                return actionResult;
            } else {
                return accepted ? InteractionResult.SUCCESS.heldItemTransformedTo(output) : InteractionResult.PASS;
            }
        } else {
            return instance.use(level, player, hand);
        }
    }

    @Inject(method = "lambda$useItem$0", at = @At("HEAD"))
    private void trackLastUsedItem(InteractionHand hand, Player player, MutableObject<InteractionResult> interactionResult, int sequence, CallbackInfoReturnable<Packet<?>> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
            ViaFabricPlusHandItemProvider.lastUsedItem = player.getItemInHand(hand).copy();
        }
    }

}
