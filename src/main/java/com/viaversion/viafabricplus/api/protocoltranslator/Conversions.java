package com.viaversion.viafabricplus.api.protocoltranslator;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Conversions {

    @Nullable Item translateItem(final ItemStack stack, final ProtocolVersion targetVersion);

    @NotNull ItemStack translateItem(final Item item, final ProtocolVersion sourceVersion);

    UserConnection createDummyUserConnection(final ProtocolVersion clientVersion, final ProtocolVersion serverVersion);

}
