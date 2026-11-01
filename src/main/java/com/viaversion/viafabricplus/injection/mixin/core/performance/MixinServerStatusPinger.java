package com.viaversion.viafabricplus.injection.mixin.core.performance;

import java.net.InetSocketAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerStatusPinger.class)
public abstract class MixinServerStatusPinger {

    @Overwrite
    public void pingLegacyServer(InetSocketAddress inetSocketAddress, final ServerAddress serverAddress, final ServerData serverData, EventLoopGroupHolder eventLoopGroupHolder) {
    }

}
