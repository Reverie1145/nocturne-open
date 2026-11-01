package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.movement;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {

    @Definition(id = "fluidTop", local = @Local(type = double.class, name = "fluidTop"))
    @Definition(id = "box", local = @Local(type = AABB.class, name = "box"))
    @Definition(id = "minY", field = "Lnet/minecraft/world/phys/AABB;minY:D")
    @Expression("fluidTop < box.minY")
    @ModifyExpressionValue(method = "update", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean removeConditional(boolean original) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) ) {
            return false;
        } else {
            return original;
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double adjustHeightCalculation(double a, double b) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return Math.max(a + 0.4, b);
        } else {
            return Math.max(a, b);
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;scale(D)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dontScaleCurrent(Vec3 instance, double scale) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return instance;
        } else {
            return instance.scale(scale);
        }
    }

}
