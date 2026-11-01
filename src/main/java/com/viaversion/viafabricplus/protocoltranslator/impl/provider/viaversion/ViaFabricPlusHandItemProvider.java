package com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import net.minecraft.world.item.ItemStack;

public final class ViaFabricPlusHandItemProvider extends HandItemProvider {

    public static ItemStack lastUsedItem = null;

    @Override
    public Item getHandItem(UserConnection info) {
        if (lastUsedItem != null && !lastUsedItem.isEmpty()) {
            return ViaFabricPlus.api().conversions().translateItem(lastUsedItem, ProtocolVersion.v1_8);
        } else {
            return null;
        }
    }

}
