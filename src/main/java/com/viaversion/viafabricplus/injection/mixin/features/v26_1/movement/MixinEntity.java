package com.viaversion.viafabricplus.injection.mixin.features.v26_1.movement;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    public boolean verticalCollision;

    @Shadow
    public abstract Vec3 getDeltaMovement();

    @Redirect(method = "move", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;horizontalCollision:Z", ordinal = 2, opcode = Opcodes.GETFIELD))
    private boolean removeVerticalCheck(Entity instance) {
        return instance.horizontalCollision || (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1) && this.verticalCollision);
    }

    @Redirect(method = "restituteMovementAfterCollisions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;verticalCollisionBelow:Z", opcode = Opcodes.GETFIELD))
    private boolean fixBelowCollisionCheck(Entity instance) {
        return instance.verticalCollisionBelow || ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1);
    }

    @Definition(id = "y", field = "Lnet/minecraft/world/phys/Vec3;y:D")
    @Definition(id = "currentMovement", local = @Local(type = Vec3.class, name = "currentMovement"))
    @Expression("-currentMovement.y < ?")
    @ModifyExpressionValue(method = "restituteMovementAfterCollisions", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean fixGravityCheck(boolean original, @Local(name = "currentMovement") Vec3 currentMovement) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return !(currentMovement.y < 0.0);
        } else {
            return original;
        }
    }

    @Redirect(method = "restituteMovementAfterCollisions", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;with(Lnet/minecraft/core/Direction$Axis;D)Lnet/minecraft/world/phys/Vec3;", ordinal = 2))
    private Vec3 fixRestitution(Vec3 instance, Direction.Axis axis, double value, @Local(name = "restitution") double restitution) {
        return instance.with(axis, ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1) ? -this.getDeltaMovement().y * restitution : value);
    }

}
