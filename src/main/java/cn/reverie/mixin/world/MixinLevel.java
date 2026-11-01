package cn.reverie.mixin.world;

import cn.reverie.manager.WorldHookManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class MixinLevel {

    @Inject(
            method = "getRainLevel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onGetRainLevel(float a, CallbackInfoReturnable<Float> cir) {
        if (WorldHookManager.shouldOverrideWeather((Object) this instanceof ClientLevel)) {
            cir.setReturnValue(WorldHookManager.effectiveRainLevel());
        }
    }

    @Inject(
            method = "getThunderLevel",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onGetThunderLevel(float a, CallbackInfoReturnable<Float> cir) {
        if (WorldHookManager.shouldOverrideWeather((Object) this instanceof ClientLevel)) {
            cir.setReturnValue(WorldHookManager.effectiveThunderLevel());
        }
    }
}
