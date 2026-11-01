package com.viaversion.viafabricplus.injection.mixin.features.v1_16_4.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityLookup.class)
public abstract class MixinEntityLookup<T> {

    @Shadow
    @Final
    private Int2ObjectMap<T> byId;

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z", remap = false))
    private boolean allowDuplicateUuid(Map<UUID, T> instance, Object o) {
        return instance.containsKey(o) && ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_16_4);
    }

    @Inject(method = "count", at = @At("HEAD"), cancellable = true)
    private void returnRealSize(CallbackInfoReturnable<Integer> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_4)) {
            cir.setReturnValue(this.byId.size());
        }
    }

}
