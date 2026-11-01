package com.viaversion.viafabricplus.injection.mixin.features.v1_16_1;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Redirect(method = "setValuesFromPositionPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;moveOrInterpolateTo(Lnet/minecraft/world/phys/Vec3;FF)V"))
    private static void cancelSmallChanges(Entity instance, Vec3 position, float yRot, float xRot) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_16_1) && Math.abs(instance.getX() - position.x) < 0.03125 && Math.abs(instance.getY() - position.y) < 0.015625 && Math.abs(instance.getZ() - position.z) < 0.03125) {
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2) && instance.getInterpolation() != null) {
                instance.getInterpolation().setInterpolationLength(0);
            }
            instance.moveOrInterpolateTo(instance.position(), yRot, xRot);
        } else {
            instance.moveOrInterpolateTo(position, yRot, xRot);
        }
    }

}
