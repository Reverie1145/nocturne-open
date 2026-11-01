package com.viaversion.viafabricplus.protocoltranslator.util;

import com.viaversion.viaversion.api.protocol.Protocol;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class ItemUtil {

    public static CompoundTag getTagOrNull(final ItemStack stack) {
        final CustomData tag = stack.get(DataComponents.CUSTOM_DATA);
        if (tag != null) {
            return tag.copyTag();
        } else {
            return null;
        }
    }

    public static String vvNbtName(final Class<? extends Protocol<?, ?, ?, ?>> protocolClass) {
        return "VV|" + protocolClass.getSimpleName();
    }

    public static String vvNbtName(final Class<? extends Protocol<?, ?, ?, ?>> protocolClass, final String name) {
        return "VV|" + protocolClass.getSimpleName() + "|" + name;
    }

}
