package com.viaversion.viafabricplus.injection.mixin.core.connection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.injection.access.core.ILocalSampleLogger;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import java.net.InetSocketAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.Connection;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.debugchart.LocalSampleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger {

    @WrapOperation(method = "pingServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;connectToServer(Ljava/net/InetSocketAddress;Lnet/minecraft/server/network/EventLoopGroupHolder;Lnet/minecraft/util/debugchart/LocalSampleLogger;)Lnet/minecraft/network/Connection;"))
    private Connection setForcedVersion(InetSocketAddress address, EventLoopGroupHolder eventLoopGroupHolder, LocalSampleLogger localSampleLogger, Operation<Connection> original, @Local(argsOnly = true) ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        if (mixinServerInfo.viaFabricPlus$forcedVersion() != null && !mixinServerInfo.viaFabricPlus$passedDirectConnectScreen()) {

            if (localSampleLogger == null) {
                localSampleLogger = new LocalSampleLogger(1);
            }

            ((ILocalSampleLogger) localSampleLogger).viaFabricPlus$setForcedVersion(mixinServerInfo.viaFabricPlus$forcedVersion());
            mixinServerInfo.viaFabricPlus$passDirectConnectScreen(false);
        }

        return original.call(address, eventLoopGroupHolder, localSampleLogger);
    }

}
