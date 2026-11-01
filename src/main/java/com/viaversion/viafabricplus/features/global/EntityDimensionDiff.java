package com.viaversion.viafabricplus.features.global;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.protocoltranslator.impl.ViaFabricPlusMappingDataLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;

public final class EntityDimensionDiff {

    private static final Map<EntityType<?>, Map<ProtocolVersion, EntityDimensions>> ENTITY_DIMENSIONS = new HashMap<>();

    public static void init() {

        final JsonObject dimensionDiff = ViaFabricPlusMappingDataLoader.INSTANCE.loadData("entity-dimensions.json");
        for (final String entity : dimensionDiff.keySet()) {
            final EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(entity)).orElse(null);
            if (entityType == null) {
                throw new IllegalStateException("Unknown entity: " + entity);
            }

            final JsonObject versions = dimensionDiff.getAsJsonObject(entity);
            final Map<ProtocolVersion, EntityDimensions> dimensionMap = new HashMap<>();
            for (final String version : versions.keySet()) {
                final ProtocolVersion protocolVersion = ProtocolVersion.getClosest(version);
                if (protocolVersion == null) {
                    throw new IllegalStateException("Unknown protocol version: " + version);
                }

                final JsonObject dimensionData = versions.getAsJsonObject(version);
                final float width = dimensionData.get("width").getAsFloat();
                final float height = dimensionData.get("height").getAsFloat();
                final float eyeHeight = dimensionData.get("eyeHeight").getAsFloat();
                final boolean fixed = dimensionData.get("fixed").getAsBoolean();
                final EntityDimensions entityDimensions = new EntityDimensions(width, height, eyeHeight, entityType.dimensions.attachments(), fixed);
                dimensionMap.put(protocolVersion, entityDimensions);
            }
            ENTITY_DIMENSIONS.put(entityType, dimensionMap);
        }

        ViaFabricPlus.api().protocolTranslation().addChangeProtocolVersionListener((oldVersion, newVersion) -> Minecraft.getInstance().execute(() -> ENTITY_DIMENSIONS.forEach((entityType, dimensionMap) -> {
            for (Map.Entry<ProtocolVersion, EntityDimensions> entry : dimensionMap.entrySet()) {
                final ProtocolVersion version = entry.getKey();
                final EntityDimensions dimensions = entry.getValue();
                if (oldVersion.newerThan(version) && newVersion.olderThanOrEqualTo(version)) {
                    entityType.dimensions = dimensions;
                    break;
                }
                if (newVersion.newerThanOrEqualTo(version) && oldVersion.olderThanOrEqualTo(version)) {
                    entityType.dimensions = dimensions;
                }
            }
        })));
    }

}
