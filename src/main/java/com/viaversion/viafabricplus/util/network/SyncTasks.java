package com.viaversion.viafabricplus.util.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class SyncTasks {

    private static final Map<String, Consumer<RegistryFriendlyByteBuf>> PENDING_EXECUTION_TASKS = new ConcurrentHashMap<>();

    public static final String PACKET_SYNC_IDENTIFIER = UUID.randomUUID() + ":" + UUID.randomUUID();

    public static void init() {
        DataCustomPayload.init();
    }

    public static String executeSyncTask(final Consumer<RegistryFriendlyByteBuf> task) {
        final String uuid = UUID.randomUUID().toString();
        PENDING_EXECUTION_TASKS.put(uuid, task);
        return uuid;
    }

    public static void handleSyncTask(final FriendlyByteBuf buf) {
        final String uuid = buf.readUtf();

        if (PENDING_EXECUTION_TASKS.containsKey(uuid)) {
            Minecraft.getInstance().execute(() -> {
                final Consumer<RegistryFriendlyByteBuf> task = PENDING_EXECUTION_TASKS.remove(uuid);
                task.accept(new RegistryFriendlyByteBuf(buf, Minecraft.getInstance().getConnection().registryAccess()));
            });
        }
    }

}
