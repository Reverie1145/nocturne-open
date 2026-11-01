package com.viaversion.viafabricplus.api.protocoltranslator;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.entity.BannerPattern;

public interface Limitations {

    default int maxChatLength() {
        return maxChatLength(ViaFabricPlus.api().targetVersion());
    }

    int maxChatLength(final ProtocolVersion version);

    boolean itemExists(final net.minecraft.world.item.Item item, final ProtocolVersion version);

    boolean enchantmentExists(final ResourceKey<Enchantment> enchantment, final ProtocolVersion version);

    boolean effectExists(final Holder<MobEffect> effect, final ProtocolVersion version);

    boolean bannerPatternExists(final ResourceKey<BannerPattern> pattern, final ProtocolVersion version);

    boolean itemExistsInConnection(final net.minecraft.world.item.Item item);

    boolean itemExistsInConnection(final ItemStack stack);

    int getStackCount(final ItemStack stack);

}
