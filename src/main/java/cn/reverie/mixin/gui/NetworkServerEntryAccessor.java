package cn.reverie.mixin.gui;

import net.minecraft.client.server.LanServer;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerSelectionList.NetworkServerEntry.class)
public interface NetworkServerEntryAccessor {

    @Accessor("serverData")
    LanServer nocturne$serverData();
}
