package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {

    @Shadow
    protected boolean jumping;

    @Shadow
    public abstract float getSpeed();

    @Shadow
    protected abstract float getFlyingSpeed();

    public MixinLivingEntity(final EntityType<?> type, final Level level) {
        super(type, level);
    }

    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;jumping:Z", opcode = Opcodes.GETFIELD))
    private boolean disableJumpOnLadder(LivingEntity self) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_13_2) && jumping;
    }

    @Inject(method = "getFrictionInfluencedSpeed", at = @At("HEAD"), cancellable = true)
    private void modifyFrictionInfluencedSpeed(float blockFriction, CallbackInfoReturnable<Float> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            float drag = this.onGround() ? blockFriction * 0.91F : 0.91F;
            float accel = 0.16277136F / (drag * drag * drag);
            cir.setReturnValue(this.onGround() ? this.getSpeed() * accel : this.getFlyingSpeed());
        }
    }

    @Redirect(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean modifyLoadedCheck(Level instance, BlockPos blockPos) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return instance.hasChunkAt(blockPos) && instance.getChunkSource().hasChunk(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        } else {
            return instance.hasChunkAt(blockPos);
        }
    }

    @Redirect(method = "travelInWater",
        slice = @Slice(from = @At(value = "FIELD", target = "Lnet/minecraft/world/effect/MobEffects;DOLPHINS_GRACE:Lnet/minecraft/core/Holder;", opcode = Opcodes.GETSTATIC)),
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;horizontalCollision:Z", ordinal = 0, opcode = Opcodes.GETFIELD))
    private boolean disableClimbing(LivingEntity instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_13_2) && instance.horizontalCollision;
    }

    @ModifyVariable(method = "getFluidFallingAdjustedMovement", at = @At("HEAD"), argsOnly = true)
    private boolean modifyMovingDown(boolean isFalling) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_13_2) && isFalling;
    }

}
