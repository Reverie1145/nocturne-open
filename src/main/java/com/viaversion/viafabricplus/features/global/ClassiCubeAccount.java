package com.viaversion.viafabricplus.features.global;

import com.viaversion.viafabricplus.ViaFabricPlusImpl;
import com.viaversion.viafabricplus.util.JsonSave;
import com.viaversion.viafabricplus.util.LegacySaveMigrator;
import de.florianreuth.classic4j.ClassiCubeHandler;
import de.florianreuth.classic4j.api.LoginProcessHandler;
import de.florianreuth.classic4j.model.classicube.account.CCAccount;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jetbrains.annotations.Nullable;

public final class ClassiCubeAccount {

    private static CCAccount account;
    private static boolean authenticated;

    public static void init() {
        final Path directory = ViaFabricPlusImpl.impl().path();
        LegacySaveMigrator.migrateClassiCubeAccount(directory);

        final Path path = directory.resolve("classicube.json");
        JsonSave.load(path, jsonObject -> account = CCAccount.fromJson(jsonObject), () -> {
            if (account != null) {
                return account.asJson();
            } else {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    ViaFabricPlusImpl.impl().logger().error("Failed to delete classicube.json!", e);
                }
                return null;
            }
        });
    }

    public static void authenticate(final @Nullable String loginCode, final LoginProcessHandler handler) {
        ClassiCubeHandler.requestAuthentication(account, loginCode, new LoginProcessHandler() {

            @Override
            public void handleSuccessfulLogin(final CCAccount account) {
                authenticated = true;
                handler.handleSuccessfulLogin(account);
            }

            @Override
            public void handleMfa(final CCAccount account) {
                handler.handleMfa(account);
            }

            @Override
            public void handleException(final Throwable throwable) {
                handler.handleException(throwable);
            }
        });
    }

    public static boolean authenticated() {
        return authenticated;
    }

    public static void set(final CCAccount account) {
        ClassiCubeAccount.account = account;
        authenticated = false;
    }

    public static CCAccount get() {
        return account;
    }

}
