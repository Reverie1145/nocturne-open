package com.viaversion.viafabricplus.injection.mixin.features.v1_21_4.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer extends LivingEntity {

    protected MixinPlayer(final EntityType<? extends LivingEntity> entityType, final Level world) {
        super(entityType, world);
    }

    @Inject(method = "canFallAtLeast", at = @At("HEAD"), cancellable = true)
    private void changeOffsetsForSneakingCollisionDetection(double deltaX, double deltaZ, double minHeight, CallbackInfoReturnable<Boolean> cir) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_4)) {
            final double constant;
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_3)) {
                constant = 0.0;
            } else {
                constant = 1.0E-5F;
            }
            final AABB box = getBoundingBox();
            cir.setReturnValue(level().noCollision(this, new AABB(box.minX + deltaX, box.minY - minHeight - constant, box.minZ + deltaZ, box.maxX + deltaX, box.minY, box.maxZ + deltaZ)));
        }
    }

}
