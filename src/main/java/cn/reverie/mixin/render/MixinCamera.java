package cn.reverie.mixin.render;

import cn.reverie.module.render.MotionCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {
    @Inject(
            method = "update(Lnet/minecraft/client/DeltaTracker;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void nocturne$applyMotionCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        MotionCamera.INSTANCE.apply((Camera) (Object) this, deltaTracker);
    }
}
