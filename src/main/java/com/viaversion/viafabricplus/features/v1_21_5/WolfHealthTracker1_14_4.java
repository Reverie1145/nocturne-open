package com.viaversion.viafabricplus.features.v1_21_5;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.StorableObject;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import net.minecraft.world.entity.LivingEntity;

public final class WolfHealthTracker1_14_4 implements StorableObject {

    private final Int2FloatMap healthDataMap = new Int2FloatOpenHashMap();

    public static float getWolfHealth(final LivingEntity entity) {
        final WolfHealthTracker1_14_4 tracker = ViaFabricPlus.api().userConnection().get(WolfHealthTracker1_14_4.class);
        if (tracker != null) {
            return tracker.getWolfHealth(entity.getId(), entity.getHealth());
        } else {

            return entity.getHealth();
        }
    }

    public float getWolfHealth(final int entityId, final float fallback) {
        return this.healthDataMap.getOrDefault(entityId, fallback);
    }

    public void setWolfHealth(final int entityId, final float wolfHealth) {
        this.healthDataMap.put(entityId, wolfHealth);
    }

}
