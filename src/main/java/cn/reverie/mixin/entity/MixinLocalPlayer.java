package cn.reverie.mixin.entity;

import cn.reverie.manager.PlayerHookManager;
import cn.reverie.manager.LegacySneakingSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Unique private boolean nocturne$bufferPhysicsYawApplied;
    @Unique private float nocturne$bufferVisibleYaw;
    @Unique private float[] nocturne$watchdogTickRotation;

    @Shadow private double xLast;
    @Shadow private double yLast;
    @Shadow private double zLast;
    @Shadow private float yRotLast;
    @Shadow private float xRotLast;
    @Shadow private boolean lastOnGround;
    @Shadow private boolean lastHorizontalCollision;
    @Shadow private int positionReminder;
    @Shadow private boolean autoJumpEnabled;
    @Shadow @Final public ClientPacketListener connection;
    @Shadow @Final protected Minecraft minecraft;

    @Shadow
    private void sendIsSprintingIfNeeded() {
    }

    @Shadow
    protected abstract boolean isControlledCamera();

    @Inject(method = "crit", at = @At("TAIL"))
    private void nocturne$onCriticalHit(Entity target, CallbackInfo ci) {
        PlayerHookManager.onCriticalHit((LocalPlayer) (Object) this, target);
    }


    @Inject(method = "tick", at = @At("HEAD"))
    private void nocturne$onTickHead(CallbackInfo ci) {
        this.nocturne$watchdogTickRotation = null;
        LocalPlayer player = (LocalPlayer) (Object) this;
        PlayerHookManager.onPlayerTick(player);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void nocturne$onAiStepHead(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        PlayerHookManager.onLivingUpdate(player);
        this.nocturne$watchdogTickRotation = PlayerHookManager.prepareAuraMovement(player);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void nocturne$applyBufferPhysicsYaw(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Float yaw = PlayerHookManager.applySilentPhysicsYaw(player);
        if (yaw != null) {
            this.nocturne$bufferVisibleYaw = player.getYRot();
            this.nocturne$bufferPhysicsYawApplied = true;
            player.setYRot(yaw);
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void nocturne$restoreBufferPhysicsYaw(CallbackInfo ci) {
        if (this.nocturne$bufferPhysicsYawApplied) {
            ((LocalPlayer) (Object) this).setYRot(this.nocturne$bufferVisibleYaw);
            this.nocturne$bufferPhysicsYawApplied = false;
        }
        PlayerHookManager.afterPlayerPhysics((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void nocturne$sendPosition(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        if (this.isControlledCamera()) {
            if (PlayerHookManager.isStuckEnabled()) {
                player.setSprinting(false);
                this.sendIsSprintingIfNeeded();
                if (PlayerHookManager.shouldHoldStuckPositionUpdate()) {
                    ci.cancel();
                    return;
                }
                this.positionReminder = 19;
            }
            PlayerHookManager.beforeMotionPacket(player);
            float[] rotation = this.nocturne$watchdogTickRotation != null
                    ? this.nocturne$watchdogTickRotation
                    : PlayerHookManager.resolveMotionRotation(player);
            float yaw = rotation[0];
            float pitch = rotation[1];
            boolean autoMlgWillPlace = PlayerHookManager.autoMlgWillPlace(player);
            boolean scaffoldPlaced = PlayerHookManager.onScaffoldPostPlace(player);
            boolean bedDefenderAttempted = PlayerHookManager.placeBedDefenderBeforePacket(player, yaw, pitch);
            boolean placed = scaffoldPlaced || autoMlgWillPlace || bedDefenderAttempted;
            if (autoMlgWillPlace) {
                PlayerHookManager.placeAutoMlgBeforePacket(player, yaw, pitch);
            }
            this.sendIsSprintingIfNeeded();
            if (player instanceof LegacySneakingSender legacySneaking) {
                legacySneaking.nocturne$sendLegacySneakingPacket();
            }
            PlayerHookManager.onSendPosition(player, yaw, pitch);
            double deltaX = player.getX() - this.xLast;
            double deltaY = player.getY() - this.yLast;
            double deltaZ = player.getZ() - this.zLast;
            double deltaYRot = yaw - this.yRotLast;
            double deltaXRot = pitch - this.xRotLast;
            ++this.positionReminder;
            boolean move = Mth.lengthSquared(deltaX, deltaY, deltaZ) > Mth.square(2.0E-4)
                    || this.positionReminder >= 20;
            boolean rot = placed || deltaYRot != 0.0 || deltaXRot != 0.0;
            boolean sentMovement = true;
            if (move && rot) {
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(player.position(), yaw, pitch, player.onGround(), player.horizontalCollision));
            } else if (move) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(player.position(), player.onGround(), player.horizontalCollision));
            } else if (rot) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            } else if (this.lastOnGround != player.onGround() || this.lastHorizontalCollision != player.horizontalCollision
                    || PlayerHookManager.shouldSendAuraIdlePacket()) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(player.onGround(), player.horizontalCollision));
            } else {
                sentMovement = false;
            }
            if (move) {
                this.xLast = player.getX();
                this.yLast = player.getY();
                this.zLast = player.getZ();
                this.positionReminder = 0;
            }
            if (rot) {
                this.yRotLast = yaw;
                this.xRotLast = pitch;
            }
            this.lastOnGround = player.onGround();
            this.lastHorizontalCollision = player.horizontalCollision;
            this.autoJumpEnabled = this.minecraft.options.autoJump().get();

            PlayerHookManager.onPostMotionPacket(player);
        } else {
            this.sendIsSprintingIfNeeded();
            if (player instanceof LegacySneakingSender legacySneaking) {
                legacySneaking.nocturne$sendLegacySneakingPacket();
            }
        }
        ci.cancel();
    }

    @Redirect(
            method = "tick",
            at = @At(value = "NEW", target = "(FFZZ)Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;")
    )
    private ServerboundMovePlayerPacket.Rot nocturne$passengerRot(float yaw, float pitch, boolean onGround, boolean horizontalCollision) {
        float[] rotation = this.nocturne$watchdogTickRotation != null
                ? this.nocturne$watchdogTickRotation
                : PlayerHookManager.passengerRotation((LocalPlayer) (Object) this);
        return new ServerboundMovePlayerPacket.Rot(rotation[0], rotation[1], onGround, horizontalCollision);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void nocturne$onTickTail(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        PlayerHookManager.afterPlayerTickPackets();
        this.nocturne$watchdogTickRotation = null;
        PlayerHookManager.onVisualTick(player);
    }

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void nocturne$autoblockSprint(CallbackInfoReturnable<Boolean> cir) {
        if (PlayerHookManager.shouldPauseAuraSprint((LocalPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(
            method = "canStartSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z")
    )
    private boolean nocturne$omniStartSprint(ClientInput input) {
        return input.hasForwardImpulse() || (hasAnyImpulse(input) && PlayerHookManager.shouldOmniSprint());
    }

    @Redirect(
            method = "shouldStopRunSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;hasForwardImpulse()Z")
    )
    private boolean nocturne$omniKeepSprint(ClientInput input) {
        return input.hasForwardImpulse() || (hasAnyImpulse(input) && PlayerHookManager.shouldOmniSprint());
    }

    private static boolean hasAnyImpulse(ClientInput input) {
        net.minecraft.world.entity.player.Input keys = input.keyPresses;
        return keys.forward() || keys.backward() || keys.left() || keys.right();
    }

    @Redirect(
            method = "modifyInput",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;itemUseSpeedMultiplier()F")
    )
    private float nocturne$itemUseSlowdown(LocalPlayer instance) {
        return PlayerHookManager.onItemUseSlowdown(((LocalPlayerAccessor) instance).nocturne$itemUseSpeedMultiplier());
    }

}
