package com.viaversion.viafabricplus.injection.mixin.features.v1_15_2.movement;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityFluidInteraction.class)
public abstract class MixinEntityFluidInteraction {

    @Definition(id = "eyeY", local = @Local(type = double.class, name = "eyeY"))
    @Definition(id = "fluidTop", local = @Local(type = double.class, name = "fluidTop"))
    @Expression("eyeY <= fluidTop")
    @ModifyExpressionValue(method = "update", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean addMagicOffset(boolean original, @Local(name = "eyeY") double eyeY, @Local(name = "fluidBottom") double fluidBottom, @Local(name = "level") BlockGetter level, @Local(name = "fluidState") FluidState fluidState, @Local(name = "mutablePos") BlockPos.MutableBlockPos mutablePos) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_15_2)) {
            return eyeY <= (fluidBottom + (fluidState.getHeight(level, mutablePos) + (0.11111111F * 2F)));
        } else {
            return original;
        }
    }

}
