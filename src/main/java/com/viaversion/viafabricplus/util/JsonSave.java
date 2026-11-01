package com.viaversion.viafabricplus.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JsonSave {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load(final Path path, final Consumer<JsonObject> reader, final Supplier<JsonObject> writer) {
        read(path, reader);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> write(path, writer)));
    }

    public static void read(final Path path, final Consumer<JsonObject> consumer) {
        if (Files.exists(path)) {
            try (final BufferedReader reader = Files.newBufferedReader(path)) {
                final JsonObject object = GSON.fromJson(reader, JsonObject.class);
                if (object != null) {
                    consumer.accept(object);
                } else {
                    ViaFabricPlusImpl.impl().logger().error("The file {} is empty!", path.getFileName());
                }
            } catch (Exception e) {
                ViaFabricPlusImpl.impl().logger().error("Failed to read file: {}!", path.getFileName(), e);
            }
        }
    }

    public static void write(final Path path, final Supplier<JsonObject> supplier) {
        final JsonObject object = supplier.get();
        if (object != null) {
            try (final BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(object, writer);
            } catch (Exception e) {
                ViaFabricPlusImpl.impl().logger().error("Failed to write file: {}!", path.getFileName(), e);
            }
        }
    }

}
