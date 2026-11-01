package cn.reverie.mixin.entity;

import cn.reverie.manager.PlayerHookManager;
import cn.reverie.module.render.NameProtect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Inject(method = "getAttackStrengthScale", at = @At("RETURN"), cancellable = true)
    private void nocturne$criticalTimerAttackCharge(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof LocalPlayer player) {
            cir.setReturnValue(PlayerHookManager.applyCriticalAttackStrengthScale(player, partialTicks, cir.getReturnValue()));
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void nocturne$protectLocalDisplayName(CallbackInfoReturnable<Component> cir) {
        Player player = (Player) (Object) this;
        String replacement = NameProtect.replacementForPlayer(player.getUUID());
        if (replacement == null) {
            return;
        }

        Component original = cir.getReturnValue();
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        String accountName = localPlayer == null ? "" : localPlayer.getGameProfile().name();
        cir.setReturnValue(NameProtect.protectDisplayName(original, accountName, replacement));
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void nocturne$stuckTravel(Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && PlayerHookManager.shouldCancelStuckTravel()) {
            ci.cancel();
            if (PlayerHookManager.shouldClearStuckVelocity()) {
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    @Unique private boolean nocturne$keepSprint;

    @Inject(method = "causeExtraKnockback", at = @At("HEAD"))
    private void nocturne$checkKeepSprint(
            Entity target,
            float knockbackStrength,
            Vec3 movement,
            DamageSource source,
            float damage,
            boolean force,
            CallbackInfo ci
    ) {
        this.nocturne$keepSprint = false;
        if (knockbackStrength > 0.0F && (Object) this instanceof LocalPlayer player) {
            this.nocturne$keepSprint = PlayerHookManager.shouldKeepSprint(player);
        }
    }

    @ModifyArg(
            method = "causeExtraKnockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"
            ),
            index = 0
    )
    private Vec3 nocturne$keepHitVelocity(Vec3 movement) {
        return this.nocturne$keepSprint ? movement.multiply(1.0 / 0.6, 1.0, 1.0 / 0.6) : movement;
    }

    @ModifyArg(
            method = "causeExtraKnockback",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"
            ),
            index = 0
    )
    private boolean nocturne$keepSprintAfterHit(boolean sprinting) {
        return this.nocturne$keepSprint || sprinting;
    }
}
