package com.viaversion.viafabricplus.screen.impl.protocol;

import com.viaversion.viafabricplus.protocoltranslator.impl.ViaFabricPlusMappingDataLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public record ProtocolVersionMetadata(@Nullable String title, LocalDate releaseDate, Identifier icon) {

    private static final Identifier UNKNOWN_ICON = Identifier.withDefaultNamespace("textures/item/barrier.png");

    private static final Map<String, ProtocolVersionMetadata> BY_VERSION_NAME = load();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US);

    public static @Nullable ProtocolVersionMetadata of(final ProtocolVersion version) {
        return BY_VERSION_NAME.get(version.getName());
    }

    public static Identifier icon(final @Nullable ProtocolVersion version) {
        final ProtocolVersionMetadata metadata = version == null ? null : of(version);
        return metadata != null ? metadata.icon() : UNKNOWN_ICON;
    }

    public Component formattedReleaseDate() {
        return Component.nullToEmpty(this.releaseDate.format(DATE_FORMAT));
    }

    private static Map<String, ProtocolVersionMetadata> load() {
        final JsonObject data = ViaFabricPlusMappingDataLoader.INSTANCE.loadData("version-metadata.json");
        final Map<String, ProtocolVersionMetadata> metadata = new HashMap<>(data.size());
        for (final String versionName : data.keySet()) {
            final JsonObject entry = data.getAsJsonObject(versionName);
            final String title = entry.has("title") ? entry.get("title").getAsString() : null;

            final Identifier icon = Identifier.withDefaultNamespace("textures/" + entry.get("icon").getAsString() + ".png");
            metadata.put(versionName, new ProtocolVersionMetadata(title, LocalDate.parse(entry.get("release_date").getAsString()), icon));
        }
        return metadata;
    }

}
