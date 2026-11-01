package com.viaversion.viafabricplus.features.c0_30cpe;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.lenni0451.reflect.Enums;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.data.ClassicProtocolExtension;
import net.raphimc.vialegacy.protocol.classic.c0_30cpetoc0_28_30.packet.ClientboundPacketsc0_30cpe;

public final class CPEAdditions {

    public final static List<ClassicProtocolExtension> ALLOWED_EXTENSIONS = new ArrayList<>();
    public final static Map<Integer, ClientboundPacketsc0_30cpe> CUSTOM_PACKETS = new HashMap<>();
    public static final List<Item> EXTENDED_CLASSIC_ITEMS = new ArrayList<>();

    public static ClientboundPacketsc0_30cpe EXT_WEATHER_TYPE;

    private static boolean snowing = false;

    public static void init() {
        allowExtension(ClassicProtocolExtension.ENV_WEATHER_TYPE);
        EXT_WEATHER_TYPE = createNewPacket(ClassicProtocolExtension.ENV_WEATHER_TYPE, 31, (user, buf) -> buf.readByte());
    }

    public static void postInit() {
        EXTENDED_CLASSIC_ITEMS.add(Items.COBBLESTONE_SLAB);
        EXTENDED_CLASSIC_ITEMS.add(Items.DEAD_BUSH);
        EXTENDED_CLASSIC_ITEMS.add(Items.SANDSTONE);
        EXTENDED_CLASSIC_ITEMS.add(Items.SNOW);
        EXTENDED_CLASSIC_ITEMS.add(Items.TORCH);
        EXTENDED_CLASSIC_ITEMS.add(Items.WOOL.brown());
        EXTENDED_CLASSIC_ITEMS.add(Items.ICE);
        EXTENDED_CLASSIC_ITEMS.add(Items.CHISELED_QUARTZ_BLOCK);
        EXTENDED_CLASSIC_ITEMS.add(Items.NETHER_QUARTZ_ORE);
        EXTENDED_CLASSIC_ITEMS.add(Items.QUARTZ_PILLAR);
        EXTENDED_CLASSIC_ITEMS.add(Items.JUKEBOX);
        EXTENDED_CLASSIC_ITEMS.add(Items.STONE_BRICKS);
    }

    public static boolean isSnowing() {
        return ViaFabricPlus.api().targetVersion().equals(LegacyProtocolVersion.c0_30cpe) && snowing;
    }

    public static void setSnowing(boolean snowing) {
        CPEAdditions.snowing = snowing;
    }

    public static void allowExtension(final ClassicProtocolExtension classicProtocolExtension) {
        ALLOWED_EXTENSIONS.add(classicProtocolExtension);
    }

    public static ClientboundPacketsc0_30cpe createNewPacket(final ClassicProtocolExtension classicProtocolExtension, final int packetId, final BiConsumer<UserConnection, ByteBuf> packetSplitter) {
        final ClientboundPacketsc0_30cpe packet = Enums.newInstance(ClientboundPacketsc0_30cpe.class, classicProtocolExtension.getName(), ClassicProtocolExtension.values().length, new Class[]{int.class, BiConsumer.class}, new Object[]{packetId, packetSplitter});
        Enums.addEnumInstance(ClientboundPacketsc0_30cpe.class, packet);
        CUSTOM_PACKETS.put(packetId, packet);

        return packet;
    }

}
