package com.viaversion.viafabricplus.injection.mixin.features.v1_20_2;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BrushItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BrushItem.class)
public abstract class MixinBrushItem {

    @Redirect(method = "calculateHitResult", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;blockInteractionRange()D"))
    private double modifyReachDistance(Player instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            return 5D;
        } else {
            return instance.blockInteractionRange();
        }
    }

}
