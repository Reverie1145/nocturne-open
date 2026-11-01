package com.viaversion.viafabricplus.injection.mixin.features.b1_8;

import com.viaversion.viafabricplus.protocoltranslator.impl.ViaFabricPlusMappingDataLoader;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.HolderSet;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataContainer;
import com.viaversion.viaversion.api.minecraft.data.StructuredDataKey;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.item.data.ToolProperties;
import com.viaversion.viaversion.api.minecraft.item.data.ToolRule;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.fastutil.ints.IntOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.ints.IntSet;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPacket1_20_3;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.Protocol1_20_3To1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPacket1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.rewriter.BlockItemPacketRewriter1_20_5;
import com.viaversion.viaversion.rewriter.ItemRewriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockItemPacketRewriter1_20_5.class, remap = false)
public abstract class MixinBlockItemPacketRewriter1_20_5 extends ItemRewriter<ClientboundPacket1_20_3, ServerboundPacket1_20_5, Protocol1_20_3To1_20_5> {

    @Unique
    private final Set<String> viaFabricPlus$foodItems_b1_7_3 = new HashSet<>();

    @Unique
    private final Map<String, Integer> viaFabricPlus$armorMaxDamage_b1_8_1 = new HashMap<>();

    @Unique
    private final Map<ProtocolVersion, Map<String, ToolProperties>> viaFabricPlus$toolDataChanges = new LinkedHashMap<>();

    public MixinBlockItemPacketRewriter1_20_5(Protocol1_20_3To1_20_5 protocol, Type<Item> itemType, Type<Item[]> itemArrayType, Type<Item> mappedItemType, Type<Item[]> mappedItemArrayType) {
        super(protocol, itemType, itemArrayType, mappedItemType, mappedItemArrayType);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void loadItemMappings(Protocol1_20_3To1_20_5 protocol, CallbackInfo ci) {

        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:apple");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:mushroom_stew");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:bread");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:porkchop");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:cooked_porkchop");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:golden_apple");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:cod");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:cooked_cod");
        this.viaFabricPlus$foodItems_b1_7_3.add("minecraft:cookie");

        final JsonObject armorMaxDamages = ViaFabricPlusMappingDataLoader.INSTANCE.loadData("armor-damages-b1.8.1.json");
        for (Map.Entry<String, JsonElement> entry : armorMaxDamages.entrySet()) {
            this.viaFabricPlus$armorMaxDamage_b1_8_1.put(entry.getKey(), entry.getValue().getAsInt());
        }

        Via.getManager().addPostEnableListener(() -> {
            final JsonObject itemToolComponents = ViaFabricPlusMappingDataLoader.INSTANCE.loadData("item-tool-components.json");
            for (Map.Entry<String, JsonElement> entry : itemToolComponents.entrySet()) {
                final ProtocolVersion version = ProtocolVersion.getClosest(entry.getKey());
                if (version == null) {
                    throw new IllegalStateException("Unknown protocol version: " + entry.getKey());
                }
                final Map<String, ToolProperties> toolProperties = new HashMap<>();
                final JsonArray toolComponents = entry.getValue().getAsJsonArray();
                for (JsonElement toolComponent : toolComponents) {
                    final JsonObject toolComponentObject = toolComponent.getAsJsonObject();
                    final String item = toolComponentObject.get("item").getAsString();
                    final float defaultMiningSpeed = toolComponentObject.get("default_mining_speed").getAsFloat();
                    final int damagePerBlock = toolComponentObject.get("damage_per_block").getAsInt();
                    final int[] suitableFor = this.viaFabricPlus$blockJsonArrayToIds(version, toolComponentObject.getAsJsonArray("suitable_for"));
                    final List<ToolRule> toolRules = new ArrayList<>();
                    final JsonArray miningSpeeds = toolComponentObject.getAsJsonArray("mining_speeds");
                    for (JsonElement miningSpeed : miningSpeeds) {
                        final JsonObject miningSpeedObject = miningSpeed.getAsJsonObject();
                        final int[] blocks = this.viaFabricPlus$blockJsonArrayToIds(version, miningSpeedObject.getAsJsonArray("blocks"));
                        final float speed = miningSpeedObject.get("speed").getAsFloat();
                        toolRules.add(new ToolRule(HolderSet.of(blocks), speed, null));
                    }
                    if (suitableFor.length > 0) {
                        toolRules.add(new ToolRule(HolderSet.of(suitableFor), null, true));
                    }
                    toolProperties.put(item, new ToolProperties(toolRules.toArray(new ToolRule[0]), defaultMiningSpeed, damagePerBlock));
                }
                this.viaFabricPlus$toolDataChanges.put(version, toolProperties);
            }
        });
    }

    @Inject(method = "appendItemDataFixComponents", at = @At("RETURN"))
    private void appendItemDataFixComponents(UserConnection connection, Item item, CallbackInfo ci) {
        final StructuredDataContainer data = item.dataContainer();
        final String identifier = this.protocol.getMappingData().getFullItemMappings().identifier(item.identifier());

        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_8tob1_8_1)) {
            if (this.viaFabricPlus$armorMaxDamage_b1_8_1.containsKey(identifier)) {
                data.set(StructuredDataKey.MAX_DAMAGE, this.viaFabricPlus$armorMaxDamage_b1_8_1.get(identifier));
            }
        }

        if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(LegacyProtocolVersion.b1_7tob1_7_3)) {
            if (this.viaFabricPlus$foodItems_b1_7_3.contains(identifier)) {
                data.set(StructuredDataKey.MAX_STACK_SIZE, 1);
                data.setEmpty(StructuredDataKey.FOOD1_20_5);
            }
        }

        for (Map.Entry<ProtocolVersion, Map<String, ToolProperties>> entry : this.viaFabricPlus$toolDataChanges.entrySet()) {
            if (connection.getProtocolInfo().serverProtocolVersion().olderThanOrEqualTo(entry.getKey())) {
                final ToolProperties toolProperties = entry.getValue().get(identifier);
                if (toolProperties != null) {
                    data.set(StructuredDataKey.TOOL1_20_5, toolProperties);
                    break;
                }
            }
        }
    }

    @Unique
    private int[] viaFabricPlus$blockJsonArrayToIds(final ProtocolVersion protocolVersion, final JsonArray jsonArray) {
        final IntSet ids = new IntOpenHashSet();
        for (final JsonElement element : jsonArray) {
            final String name = element.getAsString();
            if (name.startsWith("#")) {
                final String material = name.substring(1);
                for (Map.Entry<String, Map<ProtocolVersion, String>> entry : ViaFabricPlusMappingDataLoader.BLOCK_MATERIALS.entrySet()) {
                    for (Map.Entry<ProtocolVersion, String> materialEntry : entry.getValue().entrySet()) {
                        if (protocolVersion.olderThanOrEqualTo(materialEntry.getKey()) && materialEntry.getValue().equals(material)) {
                            ids.add(this.protocol.getMappingData().blockId(entry.getKey()));
                            break;
                        }
                    }
                }
            } else if (name.startsWith("-")) {
                ids.remove(this.protocol.getMappingData().blockId(name.substring(1)));
            } else {
                ids.add(this.protocol.getMappingData().blockId(name));
            }
        }
        return ids.toIntArray();
    }

}
