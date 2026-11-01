package com.viaversion.viafabricplus.injection.mixin.features.v1_13_2.movement;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Avatar.class)
public abstract class MixinAvatar {

    @Unique
    private static final EntityDimensions viaFabricPlus$sneaking_dimensions_v1_13_2 = EntityDimensions.scalable(0.6F, 1.65F).withEyeHeight(1.54F).
        withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT));

    @Unique
    private static final EntityDimensions viaFabricPlus$sneaking_dimensions_v1_8 = EntityDimensions.scalable(0.6F, 1.8F).withEyeHeight(1.54F).
        withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, Player.DEFAULT_VEHICLE_ATTACHMENT));

    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void modifyDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.CROUCHING) {
            if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_8)) {
                cir.setReturnValue(viaFabricPlus$sneaking_dimensions_v1_8);
            } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_13_2)) {
                cir.setReturnValue(viaFabricPlus$sneaking_dimensions_v1_13_2);
            }
        }
    }

}
