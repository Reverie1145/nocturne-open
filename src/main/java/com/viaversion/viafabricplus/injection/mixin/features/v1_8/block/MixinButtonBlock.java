package com.viaversion.viafabricplus.injection.mixin.features.v1_8.block;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ButtonBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ButtonBlock.class)
public abstract class MixinButtonBlock {

    @WrapWithCondition(
        method = "playSound",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;playSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V"))
    private boolean disableClickSounds(LevelAccessor instance, Entity except, BlockPos pos, SoundEvent soundEvent, SoundSource source) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_8);
    }

}
