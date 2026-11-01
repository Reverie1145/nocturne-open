package cn.reverie.mixin.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityFlagsAccessor {

    @Accessor("DATA_LIVING_ENTITY_FLAGS")
    static EntityDataAccessor<Byte> nocturneGetLivingEntityFlags() {
        throw new AssertionError();
    }
}
