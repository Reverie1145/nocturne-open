package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2.movement;

import com.google.common.collect.ImmutableList;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    private Level level;

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract Level level();

    @Redirect(method = {"collideWithShapes(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/List;)Lnet/minecraft/world/phys/Vec3;", "checkInsideBlocks(Ljava/util/List;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;axisStepOrder(Lnet/minecraft/world/phys/Vec3;)Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<Direction.Axis> alwaysSortYXZ(Vec3 movement) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return Direction.YXZ_AXIS_ORDER;
        } else {
            return Direction.axisStepOrder(movement);
        }
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;equal(DD)Z"))
    private static boolean horizontalExactCollisionEqualness(double a, double b) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            return a == b;
        } else {
            return Mth.equal(a, b);
        }
    }

    @Inject(method = "isInLava", at = @At("RETURN"), cancellable = true)
    private void replaceLavaCheck1_13_2(CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            final AABB aabb = this.getBoundingBox().deflate(0.1F, 0.4F, 0.1F);
            cir.setReturnValue(this.level.getBlockStatesIfLoaded(aabb).anyMatch(key -> key.getFluidState().is(FluidTags.LAVA)));
        }
    }

    @Inject(method = "getInputVector", at = @At("HEAD"), cancellable = true)
    private static void getInputVector1_13_2(Vec3 input, float speed, float yRot, CallbackInfoReturnable<Vec3> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
            float x = (float) input.x;
            float z = (float) input.z;

            float length = x * x + z * z;
            if (length < 1.0E-4F) {
                cir.setReturnValue(Vec3.ZERO);
            }

            length = Math.max(Mth.sqrt(length), 1.0F);

            final float scale = speed / length;

            x *= scale;
            z *= scale;

            final float sin = Mth.sin(yRot * (float) (Math.PI / 180.0));
            final float cos = Mth.cos(yRot * (float) (Math.PI / 180.0));

            cir.setReturnValue(new Vec3(x * cos - z * sin, input.y, z * cos + x * sin));
        }
    }

}
