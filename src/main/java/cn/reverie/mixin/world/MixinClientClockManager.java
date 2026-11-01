package cn.reverie.mixin.world;

import cn.reverie.manager.WorldHookManager;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class MixinClientClockManager {

    @Inject(
            method = "getTotalTicks",
            at = @At("HEAD"),
            cancellable = true
    )
    private void nocturne$onGetTotalTicks(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        if (WorldHookManager.isTimeChangerEnabled()) {
            cir.setReturnValue(WorldHookManager.effectiveWorldTime());
        }
    }
}
