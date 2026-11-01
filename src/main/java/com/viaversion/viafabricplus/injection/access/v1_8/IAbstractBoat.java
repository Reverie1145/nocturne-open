package com.viaversion.viafabricplus.injection.access.v1_8;

import net.minecraft.world.phys.Vec3;

public interface IAbstractBoat {

    void viaFabricPlus$setBoatInterpolationSteps(final int steps);

    Vec3 viaFabricPlus$getBoatVelocity();

    void viaFabricPlus$setBoatVelocity(final Vec3 velocity);

}
