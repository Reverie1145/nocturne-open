package com.viaversion.viafabricplus.injection.mixin.features.v1_21_5.entity;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    private Level level;

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    public abstract Level level();

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void removeLeashActions(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_5)) {
            final ItemStack itemStack = player.getItemInHand(hand);
            if (this.isAlive() && this instanceof final Leashable leashable) {
                if (leashable.getLeashHolder() != player) {
                    if (itemStack.is(Items.LEAD) && leashable.canHaveALeashAttachedTo(player)) {
                        itemStack.shrink(1);
                        cir.setReturnValue(InteractionResult.SUCCESS);
                        return;
                    }
                } else {
                    cir.setReturnValue(InteractionResult.SUCCESS.withoutItem());
                    return;
                }
            }

            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @WrapWithCondition(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;addMovementThisTick(Lnet/minecraft/world/entity/Entity$Movement;)V"))
    private boolean removeExtraCollisionChecks(Entity instance, Entity.Movement movement) {
        return ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5);
    }

}
