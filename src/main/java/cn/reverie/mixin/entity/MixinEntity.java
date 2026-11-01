package cn.reverie.mixin.entity;

import cn.reverie.manager.RenderHookManager;
import cn.reverie.manager.PlayerHookManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void nocturne$stuckMove(MoverType moverType, Vec3 delta, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && PlayerHookManager.shouldCancelStuckEntityMove()) {
            ci.cancel();
        }
    }

    @Inject(method = "getViewVector", at = @At("HEAD"), cancellable = true)
    private void nocturne$look(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }
        float[] rotation = RenderHookManager.visualLookRotation(partialTick);
        if (rotation == null) {
            return;
        }
        cir.setReturnValue(RenderHookManager.rotationVector(rotation[0], rotation[1]));
    }

    @Redirect(
            method = "moveRelative",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;getInputVector(Lnet/minecraft/world/phys/Vec3;FF)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 nocturne$strafe(Vec3 input, float speed, float yRot) {
        float yaw = yRot;
        if ((Object) this instanceof LocalPlayer) {
            Float spoofed = RenderHookManager.movementYaw();
            if (spoofed != null) {
                yaw = spoofed;
            }
        }
        return RenderHookManager.movementInputToVelocity(input, speed, yaw);
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void nocturne$getEspGlowColor(CallbackInfoReturnable<Integer> cir) {
        Integer color = RenderHookManager.glowColor((Entity) (Object) this);
        if (color != null) {
            cir.setReturnValue(color);
        }
    }
}
