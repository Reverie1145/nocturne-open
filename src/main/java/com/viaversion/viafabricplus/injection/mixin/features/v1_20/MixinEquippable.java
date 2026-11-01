package com.viaversion.viafabricplus.injection.mixin.features.v1_20;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Equippable.class)
public abstract class MixinEquippable {

    @Redirect(method = "swapWithEquipmentSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isCreative()Z"))
    private boolean removeCreativeCondition(Player instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_20) && instance.isCreative();
    }

}
