package com.viaversion.viafabricplus.api.entrypoint;

import com.viaversion.viafabricplus.api.settings.Settings;

public interface ViaFabricPlusEntrypoint {

    default void onPreLoading() {
    }

    default void onPreSettingsLoading() {
    }

    default void onPostSettingsLoading() {
    }

    default void onPostProtocolTranslationLoading() {
    }

    default void onPostRegistryLoading() {
    }

    default void onPostGameLoading() {
    }

}
