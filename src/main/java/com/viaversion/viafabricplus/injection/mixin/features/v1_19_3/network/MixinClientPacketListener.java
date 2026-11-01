package com.viaversion.viafabricplus.injection.mixin.features.v1_19_3.network;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @WrapWithCondition(method = "handlePlayerInfoUpdate", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private boolean removeUnknownPlayerListEntryWarning(Logger instance, String s, Object object1, Object object2) {
        return ViaFabricPlus.api().targetVersion().newerThanOrEqualTo(ProtocolVersion.v1_19_3);
    }

    @Redirect(method = {"handleEntityPositionSync", "handleMoveEntity"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isLocalInstanceAuthoritative()Z"))
    private boolean allowPlayerToBeMovedByEntityPackets(Entity instance) {
        if (ViaFabricPlus.api().targetVersion().olderThanOrEqualTo(ProtocolVersion.v1_19_3)) {
            return instance.getControllingPassenger() instanceof Player player ? player.isLocalPlayer() : !instance.level().isClientSide();
        } else {
            return instance.isLocalInstanceAuthoritative();
        }
    }

}
