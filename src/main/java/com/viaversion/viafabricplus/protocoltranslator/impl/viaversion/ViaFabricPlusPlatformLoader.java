package com.viaversion.viafabricplus.protocoltranslator.impl.viaversion;

import com.viaversion.viafabricplus.api.entrypoint.ViaFabricPlusEntrypoint;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusClassicMPPassProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusClassicWorldHeightProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusEncryptionProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusGameProfileFetcher;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.vialegacy.ViaFabricPlusOldAuthProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusAckSequenceProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusBaseVersionProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusCommandArgumentsProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusCompressionProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusHandItemProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusPickItemProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusPlayerAbilitiesProvider;
import com.viaversion.viafabricplus.protocoltranslator.impl.provider.viaversion.ViaFabricPlusPlayerLookTargetProvider;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.minecraft.signature.SignableCommandArgumentsProvider;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.platform.providers.ViaProviders;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocols.v1_12_2to1_13.provider.PlayerLookTargetProvider;
import com.viaversion.viaversion.protocols.v1_15_2to1_16.provider.PlayerAbilitiesProvider;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.provider.AckSequenceProvider;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.HandItemProvider;
import net.fabricmc.loader.api.FabricLoader;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.provider.ClassicMpPassProvider;
import net.raphimc.vialegacy.protocol.classic.c0_28_30toa1_0_15.provider.ClassicWorldHeightProvider;
import net.raphimc.vialegacy.protocol.release.r1_2_4_5tor1_3_1_2.provider.OldAuthProvider;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;

public final class ViaFabricPlusPlatformLoader implements ViaPlatformLoader {

    @Override
    public void load() {
        final ViaProviders providers = Via.getManager().getProviders();

        providers.use(VersionProvider.class, new ViaFabricPlusBaseVersionProvider());
        providers.use(HandItemProvider.class, new ViaFabricPlusHandItemProvider());
        providers.use(PlayerLookTargetProvider.class, new ViaFabricPlusPlayerLookTargetProvider());
        providers.use(PlayerAbilitiesProvider.class, new ViaFabricPlusPlayerAbilitiesProvider());
        providers.use(SignableCommandArgumentsProvider.class, new ViaFabricPlusCommandArgumentsProvider());
        providers.use(AckSequenceProvider.class, new ViaFabricPlusAckSequenceProvider());
        providers.use(PickItemProvider.class, new ViaFabricPlusPickItemProvider());
        providers.use(CompressionProvider.class, new ViaFabricPlusCompressionProvider());

        providers.use(OldAuthProvider.class, new ViaFabricPlusOldAuthProvider());
        providers.use(ClassicWorldHeightProvider.class, new ViaFabricPlusClassicWorldHeightProvider());
        providers.use(EncryptionProvider.class, new ViaFabricPlusEncryptionProvider());
        providers.use(GameProfileFetcher.class, new ViaFabricPlusGameProfileFetcher());
        providers.use(ClassicMpPassProvider.class, new ViaFabricPlusClassicMPPassProvider());

        FabricLoader.getInstance().invokeEntrypoints("viafabricplus", ViaFabricPlusEntrypoint.class, ViaFabricPlusEntrypoint::onPostProtocolTranslationLoading);
    }

    @Override
    public void unload() {
    }

}
