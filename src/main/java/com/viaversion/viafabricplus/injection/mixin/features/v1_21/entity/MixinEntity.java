package com.viaversion.viafabricplus.injection.mixin.features.v1_21.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    private Level level;

    @Shadow
    public abstract Level level();

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;lengthSqr()D", ordinal = 1), slice = @Slice(
        from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;")
    ))
    private double allowSmallValues(Vec3 instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return Double.MAX_VALUE;
        } else {
            return instance.lengthSqr();
        }
    }

    @Redirect(method = "setXRot", at = @At(value = "INVOKE", target = "Ljava/lang/Math;clamp(FFF)F", remap = false))
    private float dontClampPitch(float value, float min, float max) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return value;
        } else {
            return Math.clamp(value, min, max);
        }
    }

    @ModifyConstant(method = "checkInsideBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;Lit/unimi/dsi/fastutil/longs/LongSet;I)I", constant = @Constant(doubleValue = 9.999999747378752E-6))
    private double fixBlockCollisionMargin(double constant) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_1)) {
            return 1E-3;
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21)) {
            return 1E-7;
        } else {
            return constant;
        }
    }

}
