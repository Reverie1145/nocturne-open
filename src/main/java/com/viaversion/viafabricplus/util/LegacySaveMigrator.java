package com.viaversion.viafabricplus.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class LegacySaveMigrator {

    private static final String VERSION_KEY = "selected_protocol_version";
    private static final String LEGACY_VERSION_KEY = "selected-protocol-version";

    private static final Set<String> LEGACY_GROUPS = Set.of("authentication", "debug");
    private static final Map<String, String> RENAMED_SETTINGS = Map.of("beta_craft_authentication", "use_beta_craft_authentication");

    private static final String DISABLED = "disabled";

    private static final String LEGACY_ACCOUNTS_FILE = "accounts.json";
    private static final String CLASSICUBE_FILE = "classicube.json";
    private static final String CLASSICUBE_KEY = "classicube";

    public static boolean isLegacySettings(final JsonObject object) {
        return object.has(LEGACY_VERSION_KEY) || LEGACY_GROUPS.stream().anyMatch(object::has);
    }

    public static JsonObject migrateSettings(final JsonObject legacy, final JsonObject current) {
        for (final Map.Entry<String, JsonElement> group : current.entrySet()) {
            if (!group.getValue().isJsonObject()) {
                continue;
            }

            final JsonObject settings = group.getValue().getAsJsonObject();
            for (final String key : List.copyOf(settings.keySet())) {
                final JsonPrimitive value = legacyValue(legacy, RENAMED_SETTINGS.getOrDefault(key, key));
                if (value != null) {
                    settings.add(key, migrateValue(settings.get(key).getAsJsonPrimitive(), value));
                }
            }
        }

        if (legacy.has(LEGACY_VERSION_KEY)) {
            current.add(VERSION_KEY, legacy.get(LEGACY_VERSION_KEY));
        }
        return current;
    }

    public static void migrateClassiCubeAccount(final Path directory) {
        final Path legacyPath = directory.resolve(LEGACY_ACCOUNTS_FILE);
        final Path path = directory.resolve(CLASSICUBE_FILE);
        if (!Files.exists(legacyPath) || Files.exists(path)) {
            return;
        }

        JsonSave.read(legacyPath, legacy -> {
            if (legacy.has(CLASSICUBE_KEY) && legacy.get(CLASSICUBE_KEY).isJsonObject()) {
                JsonSave.write(path, () -> legacy.getAsJsonObject(CLASSICUBE_KEY));
            }

            try {
                Files.delete(legacyPath);
            } catch (final IOException e) {
                ViaFabricPlusImpl.impl().logger().error("Failed to delete the migrated {}!", LEGACY_ACCOUNTS_FILE, e);
            }
        });
    }

    private static @Nullable JsonPrimitive legacyValue(final JsonObject legacy, final String key) {
        for (final Map.Entry<String, JsonElement> group : legacy.entrySet()) {
            if (!group.getValue().isJsonObject()) {
                continue;
            }

            final JsonElement value = group.getValue().getAsJsonObject().get(key);
            if (value != null && value.isJsonPrimitive()) {
                return value.getAsJsonPrimitive();
            }
        }
        return null;
    }

    private static JsonPrimitive migrateValue(final JsonPrimitive current, final JsonPrimitive legacy) {
        if (!current.isBoolean()) {
            return new JsonPrimitive(legacy.getAsString().toUpperCase(Locale.ROOT));
        }

        return legacy.isBoolean() ? legacy : new JsonPrimitive(!DISABLED.equals(legacy.getAsString()));
    }

}
