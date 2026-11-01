package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_13_2.MouseSensitivity1_13_2;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0))
    private Object adjustMouseSensitivity1_13_2(OptionInstance<Double> instance, Operation<Double> original) {
        final Double value = original.call(instance);

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return (double) MouseSensitivity1_13_2.get1_13SliderValue(value.floatValue()).keyFloat();
        } else {
            return value;
        }
    }

}
