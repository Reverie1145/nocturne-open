package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.protocoltranslator.ProtocolTranslationImpl;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;initiateServerboundPlayConnection(Ljava/lang/String;ILnet/minecraft/network/protocol/login/ClientLoginPacketListener;)V"))
    private void disableProtocolTranslator(LevelStorageSource.LevelStorageAccess levelSourceAccess, PackRepository packRepository, WorldStem worldStem, Optional<GameRules> gameRules, boolean newWorld, CallbackInfo ci, @Local(name = "connection") Connection connection) {
        ViaFabricPlusImpl.impl().protocolTranslation().setTargetVersion(ProtocolTranslationImpl.NATIVE_VERSION, true);
        if (connection.isConnected()) {
            ViaFabricPlusImpl.impl().protocolTranslation().injectionPreviousVersionHandler(connection.channel);
        } else {
            connection.pendingActions.add(c -> ViaFabricPlusImpl.impl().protocolTranslation().injectionPreviousVersionHandler(c.channel));
        }
    }

}
