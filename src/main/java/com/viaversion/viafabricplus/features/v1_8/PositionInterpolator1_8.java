package com.viaversion.viafabricplus.features.v1_8;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.injection.access.v1_8.IAbstractBoat;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

public final class PositionInterpolator1_8 extends InterpolationHandler {

    private final AbstractBoat boatEntity;

    public PositionInterpolator1_8(final AbstractBoat entity) {
        super(entity);
        this.boatEntity = entity;
    }

    @Override
    public void interpolateTo(final Vec3 pos, final float yaw, final float pitch) {
        final IAbstractBoat mixinBoatEntity = (IAbstractBoat) this.boatEntity;
        if (  boatEntity.isVehicle() && ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_7_6)) {
            boatEntity.xo = pos.x;
            boatEntity.yo = pos.y;
            boatEntity.zo = pos.z;
            mixinBoatEntity.viaFabricPlus$setBoatInterpolationSteps(0);
            boatEntity.setPos(pos);
            boatEntity.setRot(yaw, pitch);
            boatEntity.setDeltaMovement(Vec3.ZERO);
            mixinBoatEntity.viaFabricPlus$setBoatVelocity(Vec3.ZERO);
        } else {
            if (!boatEntity.isVehicle()) {
                mixinBoatEntity.viaFabricPlus$setBoatInterpolationSteps(8);
            } else {
                if (boatEntity.distanceToSqr(pos.x, pos.y, pos.z) <= 1) {
                    return;
                }
                mixinBoatEntity.viaFabricPlus$setBoatInterpolationSteps(3);
            }

            this.interpolationData.position = pos;
            this.interpolationData.yRot = yaw;
            this.interpolationData.xRot = pitch;
            boatEntity.setDeltaMovement(mixinBoatEntity.viaFabricPlus$getBoatVelocity());
        }
    }

    @Override
    public void interpolate() {
    }

}
