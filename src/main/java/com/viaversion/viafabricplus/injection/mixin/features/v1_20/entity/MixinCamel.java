package com.viaversion.viafabricplus.injection.mixin.features.v1_20.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Camel.class)
public abstract class MixinCamel extends AbstractHorse {

    public MixinCamel(EntityType<? extends AbstractHorse> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    public void onPassengerTurned(@NonNull Entity passenger) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20) && this.getControllingPassenger() != passenger) {
            this.viaFabricPlus$clampPassengerYaw1_20_1(passenger);
        }
    }

    @Override
    protected void positionRider(@NonNull Entity passenger, @NonNull MoveFunction positionUpdater) {
        super.positionRider(passenger, positionUpdater);

        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20)) {
            this.viaFabricPlus$clampPassengerYaw1_20_1(passenger);
        }
    }

    @Unique
    private void viaFabricPlus$clampPassengerYaw1_20_1(final Entity passenger) {
        passenger.setYBodyRot(this.getYRot());
        final float passengerYaw = passenger.getYRot();

        final float deltaDegrees = Mth.wrapDegrees(passengerYaw - this.getYRot());
        final float clampedDelta = Mth.clamp(deltaDegrees, -160.0F, 160.0F);
        passenger.yRotO += clampedDelta - deltaDegrees;

        final float newYaw = passengerYaw + clampedDelta - deltaDegrees;
        passenger.setYRot(newYaw);
        passenger.setYHeadRot(newYaw);
    }

}
