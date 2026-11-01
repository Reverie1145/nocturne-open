package com.viaversion.viafabricplus.injection.mixin.features.v26_1.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.fox.Fox;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Fox.class)
public abstract class MixinFox {

    @Shadow
    @Final
    private static EntityDimensions BABY_DIMENSIONS;

    @Unique
    private static final EntityDimensions viaFabricPlus$baby_dimensions_r26_1 = EntityTypes.FOX.getDimensions().scale(0.6F).withEyeHeight(0.2975F);

    @Unique
    private static final EntityDimensions viaFabricPlus$baby_dimensions_r1_21_11 = EntityTypes.FOX.getDimensions().scale(0.5F).withEyeHeight(0.2975F);

    @Redirect(method = "getDefaultDimensions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/animal/fox/Fox;BABY_DIMENSIONS:Lnet/minecraft/world/entity/EntityDimensions;", opcode = Opcodes.GETSTATIC))
    private EntityDimensions changeBabyDimensions() {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_21_11)) {
            return viaFabricPlus$baby_dimensions_r1_21_11;
        } else if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v26_1)) {
            return viaFabricPlus$baby_dimensions_r26_1;
        } else {
            return BABY_DIMENSIONS;
        }
    }

}
