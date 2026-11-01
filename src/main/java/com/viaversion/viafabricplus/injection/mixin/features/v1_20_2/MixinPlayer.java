package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Redirect(method = "getMaxHeadRotationRelativeToBody", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isBlocking()Z"))
    private boolean dontModifyHeadRotationWhenBlocking(Player instance) {
        return ViaFabricPlus.api().targetVersion().newerThan(ProtocolVersion.v1_20_2) && instance.isBlocking();
    }

}
