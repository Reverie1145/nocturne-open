package com.viaversion.viafabricplus.injection.mixin.features.v1_12_2.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public abstract boolean hasEffect(Holder<MobEffect> effect);

    @Shadow
    protected abstract float getWaterSlowDown();

    public MixinLivingEntity(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;resetFallDistance()V"))
    private void dontResetLevitationFallDistance(LivingEntity instance) {

        if (this.hasEffect(MobEffects.SLOW_FALLING) || ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_12_2)) {
            instance.resetFallDistance();
        }
    }

    @Redirect(method = "travelInWater", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSprinting()Z", ordinal = 0))
    private boolean modifySwimSprintSpeed(LivingEntity instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_12_2) && instance.isSprinting();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double redirectFluidHeight(LivingEntity instance, TagKey<Fluid> tagKey) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && tagKey == FluidTags.WATER && this.isInWater()) {
            return 1;
        } else {
            return instance.getFluidHeight(tagKey);
        }
    }

    @Inject(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), cancellable = true)
    private void modifySwimSprintFallSpeed(double baseGravity, boolean isFalling, Vec3 movement, CallbackInfoReturnable<Vec3> ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2) && !this.isNoGravity()) {
            ci.setReturnValue(new Vec3(movement.x, movement.y - 0.02, movement.z));
        }
    }

    @ModifyConstant(method = "travelInWater", constant = @Constant(floatValue = 0.9F))
    private float modifySwimFriction(float constant) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_12_2)) {
            return this.getWaterSlowDown();
        } else {
            return constant;
        }
    }

}
