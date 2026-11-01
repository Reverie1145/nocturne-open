package com.viaversion.viafabricplus.injection.mixin.features.v1_14_4.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.features.v1_21_5.WolfHealthTracker1_14_4;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Wolf.class)
public abstract class MixinWolf extends TamableAnimal implements NeutralMob {

    protected MixinWolf(EntityType<? extends TamableAnimal> entityType, Level world) {
        super(entityType, world);
    }

    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/wolf/Wolf;getHealth()F"))
    private float fixWolfHealth(Wolf instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_14_4)) {
            return WolfHealthTracker1_14_4.getWolfHealth(this);
        } else {
            return instance.getHealth();
        }
    }

}
