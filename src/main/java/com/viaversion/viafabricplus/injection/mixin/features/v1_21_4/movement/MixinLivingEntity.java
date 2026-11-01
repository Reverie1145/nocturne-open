package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    public float xxa;

    @Shadow
    public float zza;

    public MixinLivingEntity(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
    private void moveMovementSpeedFactors(CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            this.xxa *= 0.98F;
            this.zza *= 0.98F;
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;is(Ljava/lang/Object;)Z"))
    private boolean useEuclideanDistanceCalculation(LivingEntity instance, Object o) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return false;
        } else {
            return instance.is((EntityType<?>) o);
        }
    }

    @Redirect(method = "travelFallFlying", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;onClimbable()Z"))
    private boolean dontStopGlidingWhenClimbing(LivingEntity instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_21_4) && instance.onClimbable();
    }

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;wasInPowderSnow:Z", opcode = Opcodes.GETFIELD))
    private boolean dontCheckLastTick(LivingEntity instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            return this.getInBlockState().is(Blocks.POWDER_SNOW);
        } else {
            return instance.wasInPowderSnow;
        }
    }

}
