package com.viaversion.viafabricplus.injection.mixin.features.v1_11;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(FireworkRocketItem.class)
public abstract class MixinFireworkRocketItem {

    @Redirect(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isFallFlying()Z", ordinal = 0))
    private boolean disableFireworkElytraBoost(Player player) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_11) && player.isFallFlying();
    }

}
